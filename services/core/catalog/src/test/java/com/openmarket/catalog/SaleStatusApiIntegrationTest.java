package com.openmarket.catalog;

import static com.openmarket.catalog.CatalogTokens.admin;
import static com.openmarket.catalog.CatalogTokens.buyer;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 판매 중지·재개(CAT-2), 관리자 제재(CAT-5, ADM-2). 판매자 사본은 유스케이스로 만든다. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class SaleStatusApiIntegrationTest {

    static final String PRODUCTS = "/v1/seller/products";
    static final String ADMIN_PRODUCTS = "/v1/admin/products";

    @Autowired MockMvc mockMvc;

    @Autowired RegisterSellerUseCase registerSeller;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    UUID sellerId;
    UUID adminId;

    @BeforeEach
    void approveSeller() {
        sellerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        registerSeller.execute(new RegisterSellerCommand(sellerId, "길동상회"));
    }

    @Test
    @DisplayName("판매자가 멈추면 구매자 상세에 판매 중지로 보이고, 다시 열면 판매 중이다. 이벤트 페이로드는 JSON 예시와 형식이 같다")
    void stopAndResume() throws Exception {
        UUID productId = onSale();

        mockMvc.perform(post(PRODUCTS + "/{id}/stop", productId).with(seller(sellerId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/products/{id}", productId))
                .andExpect(jsonPath("$.status").value("STOPPED"));
        mockMvc.perform(get(PRODUCTS + "/{id}", productId).with(seller(sellerId)))
                .andExpect(jsonPath("$.stopReason").value("SELLER"));

        mockMvc.perform(post(PRODUCTS + "/{id}/resume", productId).with(seller(sellerId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/products/{id}", productId))
                .andExpect(jsonPath("$.status").value("ON_SALE"));

        assertThat(eventTypes(productId))
                .containsExactly(
                        "ProductRegistered",
                        "ProductPublished",
                        "ProductStopped",
                        "ProductResumed");
        for (String type : List.of("ProductStopped", "ProductResumed")) {
            assertThat(json.readTree(payload(productId, type)).propertyNames())
                    .isEqualTo(example(type).propertyNames());
        }
    }

    @Test
    @DisplayName("등록 중인 상품은 멈출 수 없다(422 CATALOG_INVALID_STATUS)")
    void cannotStopDraft() throws Exception {
        UUID productId = registered();

        mockMvc.perform(post(PRODUCTS + "/{id}/stop", productId).with(seller(sellerId)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_STATUS"));
    }

    @Test
    @DisplayName("관리자가 제재하면 구매자에게 보이지 않고, 판매자는 재개·수정할 수 없으며, 감사 로그가 남는다")
    void adminBans() throws Exception {
        UUID productId = onSale();

        mockMvc.perform(banRequest(productId, " ").with(admin(adminId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_INPUT"));
        mockMvc.perform(banRequest(productId, "허위 광고").with(admin(adminId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(banRequest(productId, "허위 광고").with(admin(adminId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/products/{id}", productId)).andExpect(status().isNotFound());
        mockMvc.perform(post(PRODUCTS + "/{id}/resume", productId).with(seller(sellerId)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("CATALOG_PRODUCT_BANNED"));
        mockMvc.perform(get(PRODUCTS + "/{id}", productId).with(seller(sellerId)))
                .andExpect(jsonPath("$.status").value("BANNED"))
                .andExpect(jsonPath("$.banReason").value("허위 광고"));

        assertThat(eventTypes(productId)).filteredOn("ProductBanned"::equals).hasSize(1);
        assertThat(json.readTree(payload(productId, "ProductBanned")).propertyNames())
                .isEqualTo(example("ProductBanned").propertyNames());
        assertThat(
                        jdbc.sql(
                                        "SELECT actor_id || '/' || action || '/' || detail FROM audit_log"
                                                + " WHERE target_id = :id")
                                .param("id", productId.toString())
                                .query(String.class)
                                .list())
                .containsExactly(adminId + "/PRODUCT_BANNED/허위 광고");
    }

    @Test
    @DisplayName("관리자 상품 목록은 관리자만 보고, 상태와 판매자로 거른다")
    void adminList() throws Exception {
        UUID productId = onSale();
        mockMvc.perform(banRequest(productId, "허위 광고").with(admin(adminId)));

        mockMvc.perform(get(ADMIN_PRODUCTS).with(buyer(sellerId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        get(ADMIN_PRODUCTS)
                                .param("sellerId", sellerId.toString())
                                .param("status", "BANNED")
                                .with(admin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.items[0].storeName").value("길동상회"))
                .andExpect(jsonPath("$.items[0].banReason").value("허위 광고"));
        mockMvc.perform(
                        get(ADMIN_PRODUCTS)
                                .param("sellerId", sellerId.toString())
                                .param("status", "ON_SALE")
                                .with(admin(adminId)))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder banRequest(
            UUID productId, String reason) {
        return post(ADMIN_PRODUCTS + "/{id}/ban", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", reason)));
    }

    private UUID onSale() throws Exception {
        UUID productId = registered();
        mockMvc.perform(post(PRODUCTS + "/{id}/publish", productId).with(seller(sellerId)))
                .andExpect(status().isNoContent());
        return productId;
    }

    private UUID registered() throws Exception {
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
        JsonNode node = json.readTree(response);
        return UUID.fromString(node.get("productId").asString());
    }

    private List<String> eventTypes(UUID productId) {
        return jdbc.sql(
                        "SELECT type FROM outbox_event WHERE aggregate_type = 'catalog.product' AND"
                                + " aggregate_id = :id ORDER BY occurred_at, id")
                .param("id", productId.toString())
                .query(String.class)
                .list();
    }

    private String payload(UUID productId, String type) {
        return jdbc.sql(
                        "SELECT payload::text FROM outbox_event WHERE aggregate_id = :id AND type ="
                                + " :type")
                .param("id", productId.toString())
                .param("type", type)
                .query(String.class)
                .list()
                .getFirst();
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
