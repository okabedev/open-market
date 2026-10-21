package com.openmarket.order;

import static com.openmarket.order.CatalogStub.sku;
import static com.openmarket.order.OrderTokens.buyer;
import static com.openmarket.order.OrderTokens.seller;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.util.ArrayList;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 구매자 주문 목록(ORD-3)과 판매자 하위 주문 조회(ORD-4). 결제 반영은 PaymentEventsIntegrationTest가 보고, 여기서는 PAID를 DB에서
 * 바꾼다.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class OrderListApiIntegrationTest {

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
                        sku(shirt, sellerA, "길동상회", "ON_SALE", 19_900, 50),
                        sku(pants, sellerB, "춘향상점", "ON_SALE", 39_900, 50)));
    }

    @Test
    @DisplayName("구매자 주문 목록은 최근 순이고 커서로 다음 쪽을 읽는다. 다른 회원의 주문은 없다")
    void buyerList() throws Exception {
        UUID first = place(buyerId, line(shirt, 1, 19_900));
        UUID second = place(buyerId, line(shirt, 1, 19_900), line(pants, 1, 39_900));
        UUID third = place(buyerId, line(pants, 2, 39_900));
        place(UUID.randomUUID(), line(shirt, 1, 19_900));
        jdbc.sql(
                        "UPDATE orders SET status = 'CANCELLED', cancel_reason = 'STOCK_UNAVAILABLE' WHERE id = :id")
                .param("id", first)
                .update();

        JsonNode page1 = list(buyerId, null, 2);
        assertThat(page1.get("totalCount").asLong()).isEqualTo(3);
        assertThat(ids(page1, "orderId")).containsExactly(third, second);
        JsonNode top = page1.get("items").get(1);
        assertThat(top.get("status").asString()).isEqualTo("PENDING");
        assertThat(top.get("amount").asLong()).isEqualTo(19_900 + 39_900);
        assertThat(top.get("orderName").asString()).isEqualTo("반팔 티셔츠 외 1건");
        assertThat(top.has("placedAt")).isTrue();

        JsonNode page2 = list(buyerId, page1.get("nextCursor").asString(), 2);
        assertThat(ids(page2, "orderId")).containsExactly(first);
        assertThat(page2.get("items").get(0).get("cancelReason").asString())
                .isEqualTo("STOCK_UNAVAILABLE");
        assertThat(page2.get("nextCursor").isNull()).isTrue();
    }

    @Test
    @DisplayName("주문 시각이 같아도 커서 쪽 사이에 빠지거나 겹치는 주문이 없다")
    void cursorTieBreak() throws Exception {
        List<UUID> placed = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            placed.add(place(buyerId, line(shirt, 1, 19_900)));
        }
        jdbc.sql("UPDATE orders SET placed_at = '2026-10-21T00:00:00Z' WHERE buyer_id = :id")
                .param("id", buyerId)
                .update();

        List<UUID> seen = new ArrayList<>();
        String cursor = null;
        do {
            JsonNode page = list(buyerId, cursor, 2);
            seen.addAll(ids(page, "orderId"));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
        } while (cursor != null);

        assertThat(seen).hasSize(5).containsExactlyInAnyOrderElementsOf(placed);
    }

    @Test
    @DisplayName("형식이 틀린 커서는 400 ORDER_INVALID_CURSOR다")
    void invalidCursor() throws Exception {
        for (String cursor : List.of("not-base64!", "Zm9v", "bm90LWEtdGltZXxub3QtYS11dWlk")) {
            mockMvc.perform(get("/v1/orders").param("cursor", cursor).with(buyer(buyerId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ORDER_INVALID_CURSOR"));
        }
    }

    @Test
    @DisplayName("판매자는 결제된 자기 하위 주문만 목록과 상세로 본다")
    void sellerOrders() throws Exception {
        UUID paid = place(buyerId, line(shirt, 2, 19_900), line(pants, 1, 39_900));
        UUID awaiting = place(buyerId, line(shirt, 1, 19_900));
        markPaid(paid, "2026-10-21T01:00:00Z");
        jdbc.sql("UPDATE orders SET status = 'AWAITING_PAYMENT' WHERE id = :id")
                .param("id", awaiting)
                .update();
        jdbc.sql("UPDATE seller_order SET progress = 'SHIPPED' WHERE order_id = :id")
                .param("id", paid)
                .update();

        JsonNode page =
                json.readTree(
                        mockMvc.perform(get("/v1/seller/orders").with(seller(sellerA)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(page.get("totalCount").asLong()).isEqualTo(1);
        assertThat(page.get("page").asInt()).isZero();
        JsonNode item = page.get("items").get(0);
        assertThat(item.get("orderId").asString()).isEqualTo(paid.toString());
        assertThat(item.get("status").asString()).isEqualTo("PAID");
        assertThat(item.get("progress").asString()).isEqualTo("SHIPPED");
        assertThat(item.get("amount").asLong()).isEqualTo(39_800);
        assertThat(item.get("firstProductName").asString()).isEqualTo("반팔 티셔츠");
        assertThat(item.get("lineCount").asInt()).isEqualTo(1);
        assertThat(item.get("paidAt").asString()).isEqualTo("2026-10-21T01:00:00Z");
        String sellerOrderId = item.get("sellerOrderId").asString();

        mockMvc.perform(get("/v1/seller/orders/{id}", sellerOrderId).with(seller(sellerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(paid.toString()))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.progress").value("SHIPPED"))
                .andExpect(jsonPath("$.amount").value(39_800))
                .andExpect(jsonPath("$.paidAt").value("2026-10-21T01:00:00Z"))
                .andExpect(jsonPath("$.shipping.recipientName").value("홍길동"))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].skuId").value(shirt.toString()))
                .andExpect(jsonPath("$.lines[0].quantity").value(2));

        mockMvc.perform(get("/v1/seller/orders/{id}", sellerOrderId).with(seller(sellerB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mockMvc.perform(
                        get("/v1/seller/orders/{id}", sellerOrderIdOf(awaiting))
                                .with(seller(sellerA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("판매자 하위 주문 목록은 최근 결제 순이다. 먼저 주문했어도 나중에 결제했으면 앞에 온다")
    void sellerListByPaidAt() throws Exception {
        UUID placedFirst = place(buyerId, line(shirt, 1, 19_900));
        UUID placedSecond = place(buyerId, line(shirt, 2, 19_900));
        markPaid(placedFirst, "2026-10-21T02:00:00Z");
        markPaid(placedSecond, "2026-10-21T01:00:00Z");

        JsonNode page =
                json.readTree(
                        mockMvc.perform(get("/v1/seller/orders").with(seller(sellerA)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());

        assertThat(ids(page, "orderId")).containsExactly(placedFirst, placedSecond);
    }

    @Test
    @DisplayName("구매자 주문 상세는 결제된 주문에만 결제 시각이 있다")
    void buyerDetailPaidAt() throws Exception {
        UUID paid = place(buyerId, line(shirt, 1, 19_900));
        UUID pending = place(buyerId, line(shirt, 1, 19_900));
        markPaid(paid, "2026-10-21T01:00:00Z");

        mockMvc.perform(get("/v1/orders/{id}", paid).with(buyer(buyerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAt").value("2026-10-21T01:00:00Z"));
        mockMvc.perform(get("/v1/orders/{id}", pending).with(buyer(buyerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAt").doesNotExist());
    }

    @Test
    @DisplayName("판매자 역할이 없으면 판매자 조회는 403이다")
    void sellerRoleRequired() throws Exception {
        mockMvc.perform(get("/v1/seller/orders").with(buyer(buyerId)))
                .andExpect(status().isForbidden());
    }

    private JsonNode list(UUID buyer, @Nullable String cursor, int size) throws Exception {
        var request = get("/v1/orders").param("size", String.valueOf(size)).with(buyer(buyer));
        if (cursor != null) {
            request.param("cursor", cursor);
        }
        return json.readTree(
                mockMvc.perform(request)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private static List<UUID> ids(JsonNode page, String field) {
        List<UUID> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(UUID.fromString(item.get(field).asString())));
        return ids;
    }

    private void markPaid(UUID orderId, String paidAt) {
        jdbc.sql("UPDATE orders SET status = 'PAID', paid_at = :paidAt::timestamptz WHERE id = :id")
                .param("paidAt", paidAt)
                .param("id", orderId)
                .update();
    }

    private UUID sellerOrderIdOf(UUID orderId) {
        return jdbc.sql("SELECT id FROM seller_order WHERE order_id = :id")
                .param("id", orderId)
                .query(UUID.class)
                .single();
    }

    @SafeVarargs
    private UUID place(UUID buyer, Map<String, Object>... lines) throws Exception {
        Map<String, Object> body =
                Map.of(
                        "lines",
                        List.of(lines),
                        "shipping",
                        Map.of(
                                "recipientName", "홍길동",
                                "recipientPhone", "010-1234-5678",
                                "zipCode", "06236",
                                "baseAddress", "서울 강남구 테헤란로 1"));
        String response =
                mockMvc.perform(
                                post("/v1/orders")
                                        .with(buyer(buyer))
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(body)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(json.readTree(response).get("orderId").asString());
    }

    private static Map<String, Object> line(UUID skuId, int quantity, long expectedPrice) {
        return Map.of(
                "skuId", skuId.toString(), "quantity", quantity, "expectedPrice", expectedPrice);
    }
}
