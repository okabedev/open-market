package com.openmarket.order;

import static com.openmarket.order.CatalogStub.sku;
import static com.openmarket.order.OrderTokens.buyer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.order.infra.OrderExpiryJob;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 결제 기한 만료와 결제 전 구매자 취소(docs/architecture/order.md "만료와 결제 전 취소"). 결제 반영 전이라 PAID는 DB에서 바꾼다. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class OrderExpiryIntegrationTest {

    static final Path EVENTS = Path.of("src/test/resources/events");

    @DynamicPropertySource
    static void catalog(DynamicPropertyRegistry registry) {
        CatalogStub.register(registry);
    }

    @Autowired MockMvc mockMvc;

    @Autowired JsonMapper json;

    @Autowired JdbcClient jdbc;

    @Autowired OrderExpiryJob expiryJob;

    UUID buyerId;
    UUID shirt;

    @BeforeEach
    void givenCatalog() {
        CatalogStub.reset();
        buyerId = UUID.randomUUID();
        shirt = UUID.randomUUID();
        CatalogStub.skus(List.of(sku(shirt, UUID.randomUUID(), "길동상회", "ON_SALE", 19_900, 50)));
    }

    @Test
    @DisplayName("결제 기한(주문 후 15분)이 지난 PENDING·AWAITING_PAYMENT 주문을 PAYMENT_TIMEOUT으로 취소한다")
    void expiresUnpaidOrders() throws Exception {
        UUID pending = place();
        UUID awaiting = place();
        UUID paid = place();
        UUID recent = place();
        markStatus(awaiting, "AWAITING_PAYMENT");
        markStatus(paid, "PAID");
        Instant old = Instant.now().minus(Duration.ofMinutes(16));
        for (UUID id : List.of(pending, awaiting, paid)) {
            jdbc.sql("UPDATE orders SET placed_at = :at WHERE id = :id")
                    .param("at", java.sql.Timestamp.from(old))
                    .param("id", id)
                    .update();
        }

        expiryJob.expire();
        expiryJob.expire();

        for (UUID id : List.of(pending, awaiting)) {
            assertThat(row(id)).containsEntry("status", "CANCELLED");
            assertThat(row(id)).containsEntry("cancel_reason", "PAYMENT_TIMEOUT");
            assertThat(cancelledEvents(id)).hasSize(1);
        }
        JsonNode cancelled = json.readTree(cancelledEvents(pending).getFirst());
        assertThat(cancelled.propertyNames()).isEqualTo(example("OrderCancelled").propertyNames());
        assertThat(cancelled.get("reason").asString()).isEqualTo("PAYMENT_TIMEOUT");
        assertThat(row(paid)).containsEntry("status", "PAID");
        assertThat(row(recent)).containsEntry("status", "PENDING");
    }

    @Test
    @DisplayName("구매자는 결제 전 주문을 취소한다. 다시 취소해도 204이고 이벤트는 하나다")
    void buyerCancels() throws Exception {
        UUID orderId = place();
        markStatus(orderId, "AWAITING_PAYMENT");

        cancel(buyerId, orderId).andExpect(status().isNoContent());
        cancel(buyerId, orderId).andExpect(status().isNoContent());

        assertThat(row(orderId)).containsEntry("status", "CANCELLED");
        assertThat(row(orderId)).containsEntry("cancel_reason", "BUYER_CANCELLED");
        assertThat(cancelledEvents(orderId)).hasSize(1);
    }

    @Test
    @DisplayName("결제된 주문은 409 ORDER_NOT_CANCELLABLE, 다른 회원의 주문은 404 ORDER_NOT_FOUND다")
    void rejectsPaidAndOthers() throws Exception {
        UUID orderId = place();
        markStatus(orderId, "PAID");

        cancel(buyerId, orderId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
        cancel(UUID.randomUUID(), place())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        assertThat(row(orderId)).containsEntry("status", "PAID");
    }

    private org.springframework.test.web.servlet.ResultActions cancel(UUID buyer, UUID orderId)
            throws Exception {
        return mockMvc.perform(post("/v1/orders/{id}/cancel", orderId).with(buyer(buyer)));
    }

    private void markStatus(UUID orderId, String status) {
        jdbc.sql("UPDATE orders SET status = :status WHERE id = :id")
                .param("status", status)
                .param("id", orderId)
                .update();
    }

    private Map<String, Object> row(UUID orderId) {
        return jdbc.sql("SELECT status, cancel_reason FROM orders WHERE id = :id")
                .param("id", orderId)
                .query()
                .singleRow();
    }

    private List<String> cancelledEvents(UUID orderId) {
        return jdbc.sql(
                        "SELECT payload::text FROM outbox_event WHERE aggregate_id = :id AND type = 'OrderCancelled'")
                .param("id", orderId.toString())
                .query(String.class)
                .list();
    }

    private JsonNode example(String type) throws Exception {
        return json.readTree(Files.readString(EVENTS.resolve(type + ".json")));
    }

    private UUID place() throws Exception {
        Map<String, Object> body =
                Map.of(
                        "lines",
                        List.of(
                                Map.of(
                                        "skuId",
                                        shirt.toString(),
                                        "quantity",
                                        1,
                                        "expectedPrice",
                                        19_900)),
                        "shipping",
                        Map.of(
                                "recipientName", "홍길동",
                                "recipientPhone", "010-1234-5678",
                                "zipCode", "06236",
                                "baseAddress", "서울 강남구 테헤란로 1"));
        String response =
                mockMvc.perform(
                                post("/v1/orders")
                                        .with(buyer(buyerId))
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(body)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(json.readTree(response).get("orderId").asString());
    }
}
