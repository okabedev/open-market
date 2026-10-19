package com.openmarket.catalog;

import static com.openmarket.catalog.CatalogTokens.seller;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.catalog.command.application.RegisterSellerCommand;
import com.openmarket.catalog.command.application.RegisterSellerUseCase;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 재고 조정·이력(CAT-3)과 품절 전환 이벤트. 판매자 사본은 유스케이스로 만든다. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class StockApiIntegrationTest {

    static final String PRODUCTS = "/v1/seller/products";

    @Autowired MockMvc mockMvc;

    @Autowired RegisterSellerUseCase registerSeller;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    UUID sellerId;
    UUID productId;
    UUID skuId;

    @BeforeEach
    void givenProduct() throws Exception {
        sellerId = UUID.randomUUID();
        registerSeller.execute(new RegisterSellerCommand(sellerId, "길동상회"));
        Map<String, Object> body =
                Map.of(
                        "categoryId", "0199f000-0000-7000-8000-000000000101",
                        "name", "반팔 티셔츠",
                        "description", "면 100%",
                        "imageFileIds", List.of(UUID.randomUUID().toString()),
                        "skus",
                                List.of(
                                        Map.of(
                                                "optionValues",
                                                List.of(),
                                                "price",
                                                19_900,
                                                "initialStock",
                                                1)));
        String response =
                mockMvc.perform(
                                post(PRODUCTS)
                                        .with(seller(sellerId))
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(body)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        productId = UUID.fromString(json.readTree(response).get("productId").asString());
        skuId =
                jdbc.sql("SELECT id FROM sku WHERE product_id = :id")
                        .param("id", productId)
                        .query(UUID.class)
                        .single();
    }

    @Test
    @DisplayName("조정하면 남은 수량을 돌려주고, 같은 키로 다시 보내도 한 번만 반영된다. 품절되면 StockAvailabilityChanged가 나간다")
    void adjustsOnce() throws Exception {
        String key = UUID.randomUUID().toString();

        adjust(key, -1, "파손")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.onHand").value(0))
                .andExpect(jsonPath("$.reserved").value(0))
                .andExpect(jsonPath("$.available").value(0));
        adjust(key, -1, "파손")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.onHand").value(0));

        mockMvc.perform(get(PRODUCTS + "/{id}", productId).with(seller(sellerId)))
                .andExpect(jsonPath("$.skus[0].onHand").value(0));

        List<String> payloads =
                jdbc.sql(
                                "SELECT payload::text FROM outbox_event WHERE aggregate_type ="
                                        + " 'catalog.stock' AND aggregate_id = :id")
                        .param("id", productId.toString())
                        .query(String.class)
                        .list();
        assertThat(payloads).hasSize(1);
        JsonNode payload = json.readTree(payloads.getFirst());
        assertThat(payload.propertyNames())
                .isEqualTo(example("StockAvailabilityChanged").propertyNames());
        assertThat(payload.get("inStock").asBoolean()).isFalse();
        assertThat(payload.get("availabilityRevision").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("이력은 최근 순 쪽 번호 목록이다")
    void listsHistory() throws Exception {
        adjust(UUID.randomUUID().toString(), 10, "입고").andExpect(status().isCreated());
        adjust(UUID.randomUUID().toString(), -3, null).andExpect(status().isCreated());

        mockMvc.perform(
                        get(PRODUCTS + "/{id}/skus/{skuId}/stock-adjustments", productId, skuId)
                                .param("size", "1")
                                .with(seller(sellerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].delta").value(-3))
                .andExpect(jsonPath("$.items[0].onHandAfter").value(8))
                .andExpect(jsonPath("$.items[0].reason").doesNotExist());
        mockMvc.perform(
                        get(PRODUCTS + "/{id}/skus/{skuId}/stock-adjustments", productId, skuId)
                                .param("page", "1")
                                .param("size", "1")
                                .with(seller(sellerId)))
                .andExpect(jsonPath("$.items[0].delta").value(10))
                .andExpect(jsonPath("$.items[0].onHandAfter").value(11))
                .andExpect(jsonPath("$.items[0].reason").value("입고"));
    }

    @Test
    @DisplayName("예약 수량 아래로 줄이면 422, 키가 없으면 400, 다른 판매자는 404다")
    void rejects() throws Exception {
        adjust(UUID.randomUUID().toString(), -2, null)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("CATALOG_STOCK_OUT_OF_RANGE"));
        mockMvc.perform(
                        post(PRODUCTS + "/{id}/skus/{skuId}/stock-adjustments", productId, skuId)
                                .with(seller(sellerId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"delta\":1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        get(PRODUCTS + "/{id}/skus/{skuId}/stock-adjustments", productId, skuId)
                                .with(seller(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATALOG_PRODUCT_NOT_FOUND"));
        mockMvc.perform(
                        get(
                                        PRODUCTS + "/{id}/skus/{skuId}/stock-adjustments",
                                        productId,
                                        UUID.randomUUID())
                                .with(seller(sellerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATALOG_SKU_NOT_FOUND"));
    }

    private ResultActions adjust(String key, int delta, @Nullable String reason) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("delta", delta);
        if (reason != null) {
            body.put("reason", reason);
        }
        return mockMvc.perform(
                post(PRODUCTS + "/{id}/skus/{skuId}/stock-adjustments", productId, skuId)
                        .with(seller(sellerId))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    private JsonNode example(String type) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/events/" + type + ".json")) {
            if (in == null) {
                throw new IllegalStateException("events/" + type + ".json이 없다");
            }
            return json.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
