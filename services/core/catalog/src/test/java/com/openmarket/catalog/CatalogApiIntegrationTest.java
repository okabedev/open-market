package com.openmarket.catalog;

import static com.openmarket.catalog.CatalogTokens.buyer;
import static com.openmarket.catalog.CatalogTokens.seller;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.catalog.command.application.RegisterSellerCommand;
import com.openmarket.catalog.command.application.RegisterSellerUseCase;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.test.openapi.OpenApiSnapshot;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카테고리·상품 API(CAT-1, CAT-2). 판매자 사본은 이벤트 대신 유스케이스로 만든다. 리스너는 {@link SellerEventsIntegrationTest}.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class CatalogApiIntegrationTest {

    static final String PRODUCTS = "/v1/seller/products";
    static final String WOMEN = "0199f000-0000-7000-8000-000000000101";
    static final String IMAGE = "01a1536b-fce4-7842-b53c-c0268e2887fd";

    @Autowired MockMvc mockMvc;

    @Autowired RegisterSellerUseCase registerSeller;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    UUID sellerId;

    @BeforeEach
    void approveSeller() {
        sellerId = UUID.randomUUID();
        registerSeller.execute(new RegisterSellerCommand(sellerId, "길동상회"));
    }

    @Test
    @DisplayName("카테고리 트리는 토큰 없이 받는다. 대분류 8개에 소분류가 3개씩이다")
    void categoryTree() throws Exception {
        mockMvc.perform(get("/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].name").value("패션의류"))
                .andExpect(jsonPath("$[0].children.length()").value(3))
                .andExpect(jsonPath("$[0].children[0].name").value("여성의류"));
    }

    @Test
    @DisplayName("상품 등록은 판매자만 한다. 토큰이 없으면 401, 구매자면 403이다")
    void registerRequiresSeller() throws Exception {
        mockMvc.perform(json(post(PRODUCTS), registerBody(List.of(IMAGE))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(json(post(PRODUCTS).with(buyer(sellerId)), registerBody(List.of(IMAGE))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("입점 승인이 아직 반영되지 않은 판매자는 409 CATALOG_SELLER_NOT_READY다")
    void sellerCopyMissing() throws Exception {
        register(UUID.randomUUID(), List.of(IMAGE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_SELLER_NOT_READY"));
    }

    @Test
    @DisplayName("등록한 상품은 판매를 시작해야 구매자에게 보이고, 이벤트 페이로드는 JSON 예시와 형식이 같다")
    void registerPublishAndView() throws Exception {
        UUID productId = registered(List.of(IMAGE));

        mockMvc.perform(get("/v1/products/{id}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATALOG_PRODUCT_NOT_FOUND"));

        mockMvc.perform(post(PRODUCTS + "/{id}/publish", productId).with(seller(sellerId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(PRODUCTS + "/{id}/publish", productId).with(seller(sellerId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_SALE"))
                .andExpect(jsonPath("$.storeName").value("길동상회"))
                .andExpect(jsonPath("$.categoryPath[0].name").value("패션의류"))
                .andExpect(jsonPath("$.categoryPath[1].name").value("여성의류"))
                .andExpect(jsonPath("$.imageFileIds[0]").value(IMAGE))
                .andExpect(jsonPath("$.optionNames[1]").value("사이즈"))
                .andExpect(jsonPath("$.skus[0].optionValues[0]").value("블랙"))
                .andExpect(jsonPath("$.skus[0].inStock").value(true))
                .andExpect(jsonPath("$.skus[1].inStock").value(false))
                .andExpect(jsonPath("$.minPrice").value(19_900))
                .andExpect(jsonPath("$.inStock").value(true))
                .andExpect(jsonPath("$.skus[0].onHand").doesNotExist());

        assertThat(eventTypes(productId)).containsExactly("ProductRegistered", "ProductPublished");
        for (String type : List.of("ProductRegistered", "ProductPublished")) {
            assertThat(json.readTree(payload(productId, type)).propertyNames())
                    .isEqualTo(example(type).propertyNames());
        }
        assertThat(json.readTree(payload(productId, "ProductPublished")).get("revision").asLong())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("이미지 없이는 판매를 시작할 수 없다(422 CATALOG_PRODUCT_NOT_PUBLISHABLE)")
    void publishRequiresImage() throws Exception {
        UUID productId = registered(List.of());

        mockMvc.perform(post(PRODUCTS + "/{id}/publish", productId).with(seller(sellerId)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("CATALOG_PRODUCT_NOT_PUBLISHABLE"));
    }

    @Test
    @DisplayName("수정하면 가격이 바뀌고 새 옵션 조합이 재고와 함께 생기며 ProductChanged가 나간다. 판매자 목록·상세에 반영된다")
    void updateProduct() throws Exception {
        UUID productId = registered(List.of(IMAGE));
        JsonNode detail =
                read(mockMvc.perform(get(PRODUCTS + "/{id}", productId).with(seller(sellerId))));
        String m = detail.get("skus").get(0).get("skuId").asString();
        String l = detail.get("skus").get(1).get("skuId").asString();

        mockMvc.perform(
                        json(
                                put(PRODUCTS + "/{id}", productId).with(seller(sellerId)),
                                Map.of(
                                        "categoryId",
                                        WOMEN,
                                        "name",
                                        "오버핏 반팔 티셔츠",
                                        "description",
                                        "면 100%",
                                        "imageFileIds",
                                        List.of(IMAGE),
                                        "skus",
                                        List.of(
                                                Map.of(
                                                        "skuId",
                                                        m,
                                                        "optionValues",
                                                        List.of("블랙", "M"),
                                                        "price",
                                                        18_900),
                                                Map.of(
                                                        "skuId",
                                                        l,
                                                        "optionValues",
                                                        List.of("블랙", "L"),
                                                        "price",
                                                        21_900),
                                                Map.of(
                                                        "optionValues",
                                                        List.of("화이트", "M"),
                                                        "price",
                                                        19_900,
                                                        "initialStock",
                                                        4)))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(PRODUCTS + "/{id}", productId).with(seller(sellerId)))
                .andExpect(jsonPath("$.name").value("오버핏 반팔 티셔츠"))
                .andExpect(jsonPath("$.skus.length()").value(3))
                .andExpect(jsonPath("$.skus[0].price").value(18_900))
                .andExpect(jsonPath("$.skus[2].onHand").value(4))
                .andExpect(jsonPath("$.skus[2].available").value(4));
        mockMvc.perform(get(PRODUCTS).with(seller(sellerId)))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].thumbnailFileId").value(IMAGE))
                .andExpect(jsonPath("$.items[0].minPrice").value(18_900))
                .andExpect(jsonPath("$.items[0].skuCount").value(3))
                .andExpect(jsonPath("$.items[0].available").value(9));
        assertThat(eventTypes(productId)).containsExactly("ProductRegistered", "ProductChanged");
        assertThat(json.readTree(payload(productId, "ProductChanged")).propertyNames())
                .isEqualTo(example("ProductChanged").propertyNames());
    }

    @Test
    @DisplayName("다른 판매자의 상품은 조회·수정·판매 시작 모두 404다")
    void otherSellersProductIsHidden() throws Exception {
        UUID productId = registered(List.of(IMAGE));
        UUID other = UUID.randomUUID();

        mockMvc.perform(get(PRODUCTS + "/{id}", productId).with(seller(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(PRODUCTS + "/{id}/publish", productId).with(seller(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PRODUCTS).with(seller(other)))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("입력 규칙을 어기면 400 CATALOG_INVALID_INPUT이고, 대분류에는 등록할 수 없다")
    void rejectsInvalidInput() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(registerBody(List.of(IMAGE)));
        body.put("categoryId", "0199f000-0000-7000-8000-000000000100");
        mockMvc.perform(
                        json(
                                post(PRODUCTS)
                                        .with(seller(sellerId))
                                        .header("Idempotency-Key", UUID.randomUUID().toString()),
                                body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_INPUT"));

        body = new java.util.HashMap<>(registerBody(List.of(IMAGE)));
        body.put(
                "skus",
                List.of(Map.of("optionValues", List.of("블랙"), "price", 19_900, "initialStock", 1)));
        mockMvc.perform(
                        json(
                                post(PRODUCTS)
                                        .with(seller(sellerId))
                                        .header("Idempotency-Key", UUID.randomUUID().toString()),
                                body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_INPUT"));
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 다시 등록하면 상품은 하나다")
    void registerIsIdempotent() throws Exception {
        String key = UUID.randomUUID().toString();
        String first =
                mockMvc.perform(
                                json(
                                        post(PRODUCTS)
                                                .with(seller(sellerId))
                                                .header("Idempotency-Key", key),
                                        registerBody(List.of(IMAGE))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String second =
                mockMvc.perform(
                                json(
                                        post(PRODUCTS)
                                                .with(seller(sellerId))
                                                .header("Idempotency-Key", key),
                                        registerBody(List.of(IMAGE))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(json.readTree(second)).isEqualTo(json.readTree(first));
        mockMvc.perform(get(PRODUCTS).with(seller(sellerId)))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    @DisplayName("OpenAPI 문서가 프론트 api-client에 저장된 문서와 같다")
    void openApiMatchesClientSnapshot() throws Exception {
        String apiDocs =
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        OpenApiSnapshot.assertMatches("catalog", apiDocs);
    }

    private UUID registered(List<String> images) throws Exception {
        JsonNode body = read(register(sellerId, images).andExpect(status().isCreated()));
        return UUID.fromString(body.get("productId").asString());
    }

    private ResultActions register(UUID seller, List<String> images) throws Exception {
        return mockMvc.perform(
                json(
                        post(PRODUCTS)
                                .with(seller(seller))
                                .header("Idempotency-Key", UUID.randomUUID().toString()),
                        registerBody(images)));
    }

    private static Map<String, Object> registerBody(List<String> images) {
        return Map.of(
                "categoryId",
                WOMEN,
                "name",
                "반팔 티셔츠",
                "description",
                "면 100%",
                "imageFileIds",
                images,
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
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
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
                .single();
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
