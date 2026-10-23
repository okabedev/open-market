package com.openmarket.order;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
                .andExpect(jsonPath("$.sellerOrders[0].progress").doesNotExist())
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
    @DisplayName("쿠폰을 쓰면 promotion에 하위 주문 금액으로 할인을 묻고, 상세에 할인과 결제 금액을, OrderPlaced에 쓴 쿠폰을 싣는다")
    void placesOrderWithCoupons() throws Exception {
        UUID platformCoupon = UUID.randomUUID();
        UUID sellerCoupon = UUID.randomUUID();
        CatalogStub.couponDiscount(
                discount(
                        true,
                        platformCoupon,
                        3_000,
                        List.of(
                                sellerOrder(sellerA, 39_800, null, 0, 1_490, 38_310),
                                sellerOrder(sellerB, 39_900, sellerCoupon, 5_000, 1_510, 33_390)),
                        List.of()));

        UUID orderId =
                orderId(
                        place(
                                        UUID.randomUUID().toString(),
                                        List.of(platformCoupon, sellerCoupon),
                                        71_700L,
                                        line(shirt, 2, 19_900),
                                        line(pants, 1, 39_900))
                                .andExpect(status().isCreated()));

        CatalogStub.SERVER.verify(
                postRequestedFor(urlPathEqualTo(CatalogStub.COUPON_DISCOUNTS))
                        .withHeader("Authorization", equalTo("Bearer " + CatalogStub.ACCESS_TOKEN))
                        .withRequestBody(
                                equalToJson(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "buyerId",
                                                        buyerId.toString(),
                                                        "issuedCouponIds",
                                                        List.of(
                                                                platformCoupon.toString(),
                                                                sellerCoupon.toString()),
                                                        "sellerOrders",
                                                        List.of(
                                                                Map.of(
                                                                        "sellerId",
                                                                        sellerA.toString(),
                                                                        "amount",
                                                                        39_800),
                                                                Map.of(
                                                                        "sellerId",
                                                                        sellerB.toString(),
                                                                        "amount",
                                                                        39_900)))))));
        mockMvc.perform(get("/v1/orders/{id}", orderId).with(buyer(buyerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(71_700))
                .andExpect(jsonPath("$.sellerOrders[0].amount").value(39_800))
                .andExpect(jsonPath("$.sellerOrders[0].sellerCouponDiscount").value(0))
                .andExpect(jsonPath("$.sellerOrders[0].platformCouponDiscount").value(1_490))
                .andExpect(jsonPath("$.sellerOrders[0].paymentAmount").value(38_310))
                .andExpect(jsonPath("$.sellerOrders[1].sellerCouponDiscount").value(5_000))
                .andExpect(jsonPath("$.sellerOrders[1].paymentAmount").value(33_390));
        JsonNode placed = json.readTree(outboxPayload(orderId, "OrderPlaced"));
        assertThat(placed.get("issuedCouponIds").get(0).asString())
                .isEqualTo(platformCoupon.toString());
        assertThat(placed.get("issuedCouponIds").get(1).asString())
                .isEqualTo(sellerCoupon.toString());
    }

    @Test
    @DisplayName(
            "쓸 수 없는 쿠폰이면 422 ORDER_COUPON_NOT_APPLICABLE에 쿠폰마다 이유를 담고, promotion을 부를 수 없으면 503이다")
    void rejectsUnavailableCoupons() throws Exception {
        UUID coupon = UUID.randomUUID();
        CatalogStub.couponDiscount(
                discount(
                        false,
                        null,
                        0,
                        List.of(sellerOrder(sellerA, 19_900, null, 0, 0, 19_900)),
                        List.of(Map.of("issuedCouponId", coupon.toString(), "reason", "EXPIRED"))));

        place(UUID.randomUUID().toString(), List.of(coupon), null, line(shirt, 1, 19_900))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("ORDER_COUPON_NOT_APPLICABLE"))
                .andExpect(jsonPath("$.coupons[0].issuedCouponId").value(coupon.toString()))
                .andExpect(jsonPath("$.coupons[0].reason").value("EXPIRED"));

        CatalogStub.couponDiscountFail(500);
        place(UUID.randomUUID().toString(), List.of(coupon), null, line(shirt, 1, 19_900))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ORDER_PROMOTION_UNAVAILABLE"));
        assertThat(
                        jdbc.sql("SELECT count(*) FROM orders WHERE buyer_id = :id")
                                .param("id", buyerId)
                                .query(Integer.class)
                                .single())
                .isZero();
    }

    @Test
    @DisplayName("쿠폰이 없는 주문은 promotion을 부르지 않는다. 결제 금액을 보내면 비교해 다르면 409 ORDER_AMOUNT_CHANGED다")
    void expectedAmount() throws Exception {
        place(UUID.randomUUID().toString(), List.of(), 19_000L, line(shirt, 1, 19_900))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_AMOUNT_CHANGED"))
                .andExpect(jsonPath("$.currentAmount").value(19_900));
        place(UUID.randomUUID().toString(), List.of(), 19_900L, line(shirt, 1, 19_900))
                .andExpect(status().isCreated());

        CatalogStub.SERVER.verify(
                0, postRequestedFor(urlPathEqualTo(CatalogStub.COUPON_DISCOUNTS)));
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
        return place(key, null, null, lines);
    }

    private ResultActions place(
            String key,
            @Nullable List<UUID> issuedCouponIds,
            @Nullable Long expectedAmount,
            Map<?, ?>... lines)
            throws Exception {
        Map<String, @Nullable Object> body = new HashMap<>();
        body.put("lines", List.of(lines));
        body.put(
                "shipping",
                Map.of(
                        "recipientName", "홍길동",
                        "recipientPhone", "010-1234-5678",
                        "zipCode", "06236",
                        "baseAddress", "서울 강남구 테헤란로 1",
                        "detailAddress", "101호"));
        if (issuedCouponIds != null) {
            body.put("issuedCouponIds", issuedCouponIds.stream().map(UUID::toString).toList());
        }
        if (expectedAmount != null) {
            body.put("expectedAmount", expectedAmount);
        }
        return mockMvc.perform(
                post("/v1/orders")
                        .with(buyer(buyerId))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    /** promotion 할인 계산 응답(docs/architecture/promotion.md "할인 계산 API(내부)"). */
    private static Map<String, @Nullable Object> discount(
            boolean applicable,
            @Nullable UUID platformIssuedCouponId,
            long platformCouponDiscount,
            List<Map<String, @Nullable Object>> sellerOrders,
            List<Map<String, Object>> unavailable) {
        Map<String, @Nullable Object> response = new HashMap<>();
        response.put("applicable", applicable);
        response.put("sellerOrders", sellerOrders);
        response.put(
                "platformIssuedCouponId",
                platformIssuedCouponId == null ? null : platformIssuedCouponId.toString());
        response.put("platformCouponDiscount", platformCouponDiscount);
        response.put(
                "paymentAmount",
                sellerOrders.stream()
                        .mapToLong(s -> (long) Objects.requireNonNull(s.get("paymentAmount")))
                        .sum());
        response.put("unavailable", unavailable);
        return response;
    }

    private static Map<String, @Nullable Object> sellerOrder(
            UUID sellerId,
            long amount,
            @Nullable UUID sellerIssuedCouponId,
            long sellerCouponDiscount,
            long platformCouponDiscount,
            long paymentAmount) {
        Map<String, @Nullable Object> sellerOrder = new HashMap<>();
        sellerOrder.put("sellerId", sellerId.toString());
        sellerOrder.put("amount", amount);
        sellerOrder.put(
                "sellerIssuedCouponId",
                sellerIssuedCouponId == null ? null : sellerIssuedCouponId.toString());
        sellerOrder.put("sellerCouponDiscount", sellerCouponDiscount);
        sellerOrder.put("platformCouponDiscount", platformCouponDiscount);
        sellerOrder.put("paymentAmount", paymentAmount);
        return sellerOrder;
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
