package com.openmarket.catalog;

import static com.openmarket.catalog.CatalogTokens.buyer;
import static com.openmarket.catalog.CatalogTokens.internal;
import static com.openmarket.catalog.CatalogTokens.seller;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.catalog.command.application.RegisterSellerCommand;
import com.openmarket.catalog.command.application.RegisterSellerUseCase;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** order가 부르는 SKU 일괄 조회(내부 API). 서비스 간 토큰({@code internal} scope)만 부른다. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class InternalSkuApiIntegrationTest {

    static final String PRODUCTS = "/v1/seller/products";
    static final String SKUS = "/internal/v1/skus";
    static final String IMAGE = "01a1536b-fce4-7842-b53c-c0268e2887fd";

    @Autowired MockMvc mockMvc;

    @Autowired RegisterSellerUseCase registerSeller;

    @Autowired JsonMapper json;

    UUID sellerId;
    UUID productId;
    UUID inStockSku;
    UUID soldOutSku;

    @BeforeEach
    void givenProduct() throws Exception {
        sellerId = UUID.randomUUID();
        registerSeller.execute(new RegisterSellerCommand(sellerId, "길동상회"));
        Map<String, Object> body =
                Map.of(
                        "categoryId",
                        "0199f000-0000-7000-8000-000000000101",
                        "name",
                        "반팔 티셔츠",
                        "description",
                        "면 100%",
                        "imageFileIds",
                        List.of(IMAGE),
                        "optionNames",
                        List.of("색상", "사이즈"),
                        "skus",
                        List.of(
                                Map.of(
                                        "optionValues",
                                        List.of("블랙", "M"),
                                        "price",
                                        19_900,
                                        "initialStock",
                                        5),
                                Map.of(
                                        "optionValues",
                                        List.of("블랙", "L"),
                                        "price",
                                        21_900,
                                        "initialStock",
                                        0)));
        productId =
                UUID.fromString(
                        read(mockMvc.perform(
                                                post(PRODUCTS)
                                                        .with(seller(sellerId))
                                                        .header(
                                                                "Idempotency-Key",
                                                                UUID.randomUUID().toString())
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(json.writeValueAsString(body)))
                                        .andExpect(status().isCreated())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                                .get("productId")
                                .asString());
        JsonNode detail =
                read(
                        mockMvc.perform(get(PRODUCTS + "/{id}", productId).with(seller(sellerId)))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        inStockSku = UUID.fromString(detail.get("skus").get(0).get("skuId").asString());
        soldOutSku = UUID.fromString(detail.get("skus").get(1).get("skuId").asString());
    }

    @Test
    @DisplayName("SKU마다 상품명, 판매 상태, 판매자, 옵션, 가격, 판매 가능 수량, 대표 이미지를 준다. 없는 SKU는 빠진다")
    void lookup() throws Exception {
        UUID missing = UUID.randomUUID();

        mockMvc.perform(
                        get(SKUS)
                                .param("ids", inStockSku + "," + soldOutSku + "," + missing)
                                .with(internal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(
                        jsonPath("$[?(@.skuId=='%s')].productId", inStockSku)
                                .value(productId.toString()))
                .andExpect(jsonPath("$[?(@.skuId=='%s')].productName", inStockSku).value("반팔 티셔츠"))
                .andExpect(jsonPath("$[?(@.skuId=='%s')].productStatus", inStockSku).value("DRAFT"))
                .andExpect(
                        jsonPath("$[?(@.skuId=='%s')].sellerId", inStockSku)
                                .value(sellerId.toString()))
                .andExpect(jsonPath("$[?(@.skuId=='%s')].storeName", inStockSku).value("길동상회"))
                .andExpect(
                        jsonPath("$[?(@.skuId=='%s')].categoryId", inStockSku)
                                .value("0199f000-0000-7000-8000-000000000101"))
                .andExpect(jsonPath("$[?(@.skuId=='%s')].optionNames[1]", inStockSku).value("사이즈"))
                .andExpect(jsonPath("$[?(@.skuId=='%s')].optionValues[1]", inStockSku).value("M"))
                .andExpect(jsonPath("$[?(@.skuId=='%s')].price", inStockSku).value(19_900))
                .andExpect(jsonPath("$[?(@.skuId=='%s')].availableQuantity", inStockSku).value(5))
                .andExpect(jsonPath("$[?(@.skuId=='%s')].thumbnailFileId", inStockSku).value(IMAGE))
                .andExpect(jsonPath("$[?(@.skuId=='%s')].availableQuantity", soldOutSku).value(0))
                .andExpect(jsonPath("$[?(@.skuId=='%s')].price", soldOutSku).value(21_900));
    }

    @Test
    @DisplayName("판매를 시작하면 판매 상태가 ON_SALE로 나온다")
    void reflectsSaleStatus() throws Exception {
        mockMvc.perform(post(PRODUCTS + "/{id}/publish", productId).with(seller(sellerId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(SKUS).param("ids", inStockSku.toString()).with(internal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productStatus").value("ON_SALE"));
    }

    @Test
    @DisplayName("사용자 토큰으로는 부를 수 없다(403). 토큰이 없으면 401이다")
    void requiresServiceToken() throws Exception {
        mockMvc.perform(get(SKUS).param("ids", inStockSku.toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(SKUS).param("ids", inStockSku.toString()).with(buyer(sellerId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ID는 1~100개다. 어기면 400 CATALOG_INVALID_INPUT이다")
    void idCount() throws Exception {
        String tooMany =
                String.join(
                        ",",
                        IntStream.range(0, 101)
                                .mapToObj(i -> UUID.randomUUID().toString())
                                .toList());

        mockMvc.perform(get(SKUS).param("ids", tooMany).with(internal()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_INPUT"));
        mockMvc.perform(get(SKUS).param("ids", "").with(internal()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_INPUT"));
    }

    @Test
    @DisplayName("내부 API는 OpenAPI 문서에 나오지 않는다")
    void notInApiDocs() throws Exception {
        String apiDocs =
                mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();

        assertThat(apiDocs).doesNotContain("/internal/");
    }

    private JsonNode read(String body) {
        return json.readTree(body);
    }
}
