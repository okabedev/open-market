package com.openmarket.order;

import static com.openmarket.order.CatalogStub.sku;
import static com.openmarket.order.OrderTokens.buyer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 주문 생성과 구매자 상세(ORD-2, ORD-3). catalog와 auth 토큰 엔드포인트는 WireMock({@link CatalogStub})이다. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class OrderApiIntegrationTest {

    static final Path EVENTS = Path.of("src/test/resources/events");

    @DynamicPropertySource
    static void catalog(DynamicPropertyRegistry registry) {
        CatalogStub.register(registry);
    }

    @Autowired MockMvc mockMvc;

    @Autowired JsonMapper json;

    @Autowired JdbcClient jdbc;

    UUID buyerId;
    UUID sellerA;
    UUID sellerB;
    UUID shirt;
    UUID pants;

    @BeforeEach
    void givenCatalog() {
        CatalogStub.reset();
        buyerId = UUID.randomUUID();
        sellerA = UUID.randomUUID();
        sellerB = UUID.randomUUID();
        shirt = UUID.randomUUID();
        pants = UUID.randomUUID();
        CatalogStub.skus(
                List.of(
                        sku(shirt, sellerA, "길동상회", "ON_SALE", 19_900, 5),
                        sku(pants, sellerB, "춘향상점", "ON_SALE", 39_900, 1)));
    }

    @Test
    @DisplayName("주문하면 PENDING으로 만들고 하위 주문을 판매자별로 나눈다. OrderPlaced는 JSON 예시와 형식이 같다")
    void placesOrder() throws Exception {
        UUID orderId =
                orderId(
                        place(
                                        UUID.randomUUID().toString(),
                                        line(shirt, 2, 19_900),
                                        line(pants, 1, 39_900))
                                .andExpect(status().isCreated()));

        mockMvc.perform(get("/v1/orders/{id}", orderId).with(buyer(buyerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.cancelReason").doesNotExist())
                .andExpect(jsonPath("$.amount").value(19_900 * 2 + 39_900))
                .andExpect(jsonPath("$.orderName").value("반팔 티셔츠 외 1건"))
                .andExpect(jsonPath("$.shipping.recipientName").value("홍길동"))
                .andExpect(jsonPath("$.shipping.detailAddress").value("101호"))
                .andExpect(jsonPath("$.sellerOrders.length()").value(2))
                .andExpect(jsonPath("$.sellerOrders[0].storeName").value("길동상회"))
                .andExpect(jsonPath("$.sellerOrders[0].amount").value(39_800))
                .andExpect(jsonPath("$.sellerOrders[0].lines[0].skuId").value(shirt.toString()))
                .andExpect(jsonPath("$.sellerOrders[0].lines[0].optionNames[0]").value("색상"))
                .andExpect(jsonPath("$.sellerOrders[0].lines[0].optionValues[0]").value("블랙"))
                .andExpect(jsonPath("$.sellerOrders[0].lines[0].unitPrice").value(19_900))
                .andExpect(jsonPath("$.sellerOrders[0].lines[0].quantity").value(2))
                .andExpect(jsonPath("$.sellerOrders[1].storeName").value("춘향상점"));

        JsonNode placed = json.readTree(outboxPayload(orderId, "OrderPlaced"));
        assertThat(placed.propertyNames()).isEqualTo(example("OrderPlaced").propertyNames());
        assertThat(placed.get("lines").get(0).propertyNames())
                .isEqualTo(example("OrderPlaced").get("lines").get(0).propertyNames());
        assertThat(placed.get("buyerId").asString()).isEqualTo(buyerId.toString());
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 다시 보내면 같은 주문을 돌려주고 주문은 하나다")
    void idempotent() throws Exception {
        String key = UUID.randomUUID().toString();
        UUID first = orderId(place(key, line(shirt, 1, 19_900)).andExpect(status().isCreated()));
        UUID second = orderId(place(key, line(shirt, 1, 19_900)).andExpect(status().isCreated()));

        assertThat(second).isEqualTo(first);
        assertThat(
                        jdbc.sql("SELECT count(*) FROM orders WHERE buyer_id = :id")
                                .param("id", buyerId)
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("가격이 바뀌면 409 ORDER_PRICE_CHANGED에 바뀐 줄을 담고, 수량이 모자라면 422에 SKU를 담는다")
    void rejectsChangedPriceAndShortStock() throws Exception {
        place(UUID.randomUUID().toString(), line(shirt, 1, 18_900), line(pants, 1, 39_900))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_PRICE_CHANGED"))
                .andExpect(jsonPath("$.changes.length()").value(1))
                .andExpect(jsonPath("$.changes[0].skuId").value(shirt.toString()))
                .andExpect(jsonPath("$.changes[0].expectedPrice").value(18_900))
                .andExpect(jsonPath("$.changes[0].currentPrice").value(19_900));

        place(UUID.randomUUID().toString(), line(pants, 2, 39_900))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("ORDER_INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.skuIds[0]").value(pants.toString()));
        assertThat(
                        jdbc.sql("SELECT count(*) FROM orders WHERE buyer_id = :id")
                                .param("id", buyerId)
                                .query(Integer.class)
                                .single())
                .isZero();
    }

    @Test
    @DisplayName("다른 회원의 주문은 404 ORDER_NOT_FOUND다")
    void othersOrderIsHidden() throws Exception {
        UUID orderId =
                orderId(
                        place(UUID.randomUUID().toString(), line(shirt, 1, 19_900))
                                .andExpect(status().isCreated()));

        mockMvc.perform(get("/v1/orders/{id}", orderId).with(buyer(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("배송지 규칙을 어기면 catalog를 부르지 않고 400 ORDER_INVALID_INPUT이다")
    void invalidShipping() throws Exception {
        Map<String, Object> body =
                Map.of(
                        "lines",
                        List.of(line(shirt, 1, 19_900)),
                        "shipping",
                        Map.of(
                                "recipientName", "홍길동",
                                "recipientPhone", "01012345678",
                                "zipCode", "06236",
                                "baseAddress", "서울"));

        mockMvc.perform(
                        post("/v1/orders")
                                .with(buyer(buyerId))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_INVALID_INPUT"));
    }

    private ResultActions place(String key, Map<?, ?>... lines) throws Exception {
        Map<String, Object> body =
                Map.of(
                        "lines",
                        List.of(lines),
                        "shipping",
                        Map.of(
                                "recipientName", "홍길동",
                                "recipientPhone", "010-1234-5678",
                                "zipCode", "06236",
                                "baseAddress", "서울 강남구 테헤란로 1",
                                "detailAddress", "101호"));
        return mockMvc.perform(
                post("/v1/orders")
                        .with(buyer(buyerId))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    private static Map<String, Object> line(UUID skuId, int quantity, long expectedPrice) {
        return Map.of(
                "skuId", skuId.toString(), "quantity", quantity, "expectedPrice", expectedPrice);
    }

    private UUID orderId(ResultActions result) throws Exception {
        return UUID.fromString(
                json.readTree(result.andReturn().getResponse().getContentAsString())
                        .get("orderId")
                        .asString());
    }

    private String outboxPayload(UUID orderId, String type) {
        return jdbc.sql(
                        "SELECT payload::text FROM outbox_event WHERE aggregate_id = :id AND type = :type")
                .param("id", orderId.toString())
                .param("type", type)
                .query(String.class)
                .single();
    }

    private JsonNode example(String type) throws Exception {
        return json.readTree(Files.readString(EVENTS.resolve(type + ".json")));
    }
}
