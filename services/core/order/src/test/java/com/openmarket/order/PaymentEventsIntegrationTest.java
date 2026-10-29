package com.openmarket.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.order.command.domain.CartRepository;
import com.openmarket.order.command.domain.Order;
import com.openmarket.order.command.domain.OrderItem;
import com.openmarket.order.command.domain.OrderRepository;
import com.openmarket.order.command.domain.ShippingAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * payment가 낸 결제 결과(Debezium과 같은 형식) → 리스너 → 주문 상태(docs/architecture/order.md "결제 결과"). 페이로드는
 * 발행자(payment)의 JSON 예시를 읽어 orderId만 바꾼다.
 */
@SpringBootTest
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    PaymentEventsIntegrationTest.Topics.class
})
class PaymentEventsIntegrationTest {

    static final String TOPIC = "payment.payment";
    static final Path PUBLISHER_EXAMPLES = Path.of("../payment/src/test/resources/events");
    static final Path EVENTS = Path.of("src/test/resources/events");

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic paymentPayment() {
            return TopicBuilder.name(TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic paymentPaymentDlt() {
            return TopicBuilder.name(TOPIC + ".DLT").partitions(1).build();
        }
    }

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired KafkaContainer kafka;

    @Autowired OrderRepository orders;

    @Autowired CartRepository carts;

    @Autowired TransactionTemplate transaction;

    KafkaProducer<String, String> producer;

    @BeforeEach
    void setUp() {
        producer =
                new KafkaProducer<>(
                        Map.of(
                                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                kafka.getBootstrapServers()),
                        new StringSerializer(),
                        new StringSerializer());
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    @DisplayName(
            "PaymentApproved를 받으면 결제 완료가 되고 OrderPaid를 내고 장바구니에서 주문한 SKU를 뺀다. 같은 이벤트가 다시 와도 한 번만 한다")
    void pays() throws Exception {
        UUID buyerId = UUID.randomUUID();
        UUID ordered = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID orderId = awaitingOrder(buyerId, ordered);
        cart(buyerId, ordered, other);
        UUID eventId = UUID.randomUUID();
        String approved = withOrderId("PaymentApproved.json", orderId, 59_700L);

        send(eventId, "PaymentApproved", approved, orderId);
        send(eventId, "PaymentApproved", approved, orderId);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(status(orderId)).isEqualTo("PAID"));
        assertThat(
                        jdbc.sql("SELECT paid_at FROM orders WHERE id = :id")
                                .param("id", orderId)
                                .query(Instant.class)
                                .single())
                .isEqualTo(Instant.parse(json.readTree(approved).get("approvedAt").asString()));
        assertThat(outboxTypes(orderId))
                .containsExactly("OrderPlaced", "OrderAwaitingPayment", "OrderPaid");
        assertThat(cartSkus(buyerId)).containsExactly(other);

        JsonNode paid = json.readTree(outboxPayload(orderId, "OrderPaid"));
        JsonNode example = example("OrderPaid");
        assertThat(paid.propertyNames()).isEqualTo(example.propertyNames());
        JsonNode sellerOrder = paid.get("sellerOrders").get(0);
        assertThat(sellerOrder.propertyNames())
                .isEqualTo(example.get("sellerOrders").get(0).propertyNames());
        assertThat(sellerOrder.get("lines").get(0).propertyNames())
                .isEqualTo(example.get("sellerOrders").get(0).get("lines").get(0).propertyNames());
        // 배송지는 싣지 않는다. 판매자는 하위 주문 상세에서 받는다(NFR-7)
        assertThat(paid.has("shippingAddress")).isFalse();
        assertThat(paid.toString()).doesNotContain("010-");
        assertThat(paid.get("amount").asLong()).isEqualTo(59_700);
        assertThat(sellerOrder.get("lines").get(0).get("skuId").asString())
                .isEqualTo(ordered.toString());
    }

    @Test
    @DisplayName("PaymentFailed는 처리하지 않는다. 주문은 결제 대기로 남아 다시 결제할 수 있다")
    void ignoresFailure() throws Exception {
        UUID orderId = awaitingOrder(UUID.randomUUID(), UUID.randomUUID());
        UUID marker = awaitingOrder(UUID.randomUUID(), UUID.randomUUID());

        send(
                UUID.randomUUID(),
                "PaymentFailed",
                withOrderId("PaymentFailed.json", orderId, null),
                orderId);
        // 같은 파티션의 다음 이벤트가 처리되면 앞선 PaymentFailed도 지나갔다.
        send(
                UUID.randomUUID(),
                "PaymentApproved",
                withOrderId("PaymentApproved.json", marker, 59_700L),
                marker);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(status(marker)).isEqualTo("PAID"));
        assertThat(status(orderId)).isEqualTo("AWAITING_PAYMENT");
        assertThat(outboxTypes(orderId)).containsExactly("OrderPlaced", "OrderAwaitingPayment");
    }

    /** 결제 대기 주문을 저장하고 앞선 이벤트를 outbox에 넣는다(예약 결과를 반영한 것과 같은 결과). */
    private UUID awaitingOrder(UUID buyerId, UUID skuId) {
        Order order =
                Order.place(
                        buyerId,
                        List.of(
                                new OrderItem(
                                        skuId,
                                        UUID.randomUUID(),
                                        "반팔 티셔츠",
                                        UUID.randomUUID(),
                                        "길동상회",
                                        UUID.randomUUID(),
                                        List.of("색상"),
                                        List.of("블랙"),
                                        19_900,
                                        3)),
                        ShippingAddress.of("홍길동", "010-1234-5678", "06236", "주소", null),
                        Instant.now());
        order.recordReservation(Order.Reservation.STOCK, Instant.now());
        transaction.executeWithoutResult(status -> orders.save(order));
        for (String type : List.of("OrderPlaced", "OrderAwaitingPayment")) {
            jdbc.sql(
                            "INSERT INTO outbox_event (id, aggregate_type, aggregate_id, type, payload,"
                                    + " schema_version, occurred_at) VALUES (:id, 'order.order',"
                                    + " :orderId, :type, '{}'::jsonb, 1, now())")
                    .param("id", UUID.randomUUID())
                    .param("orderId", order.id().toString())
                    .param("type", type)
                    .update();
        }
        return order.id();
    }

    private void cart(UUID buyerId, UUID... skuIds) {
        transaction.executeWithoutResult(
                status -> {
                    carts.createIfAbsent(buyerId);
                    var cart = carts.findForUpdateByMemberId(buyerId).orElseThrow();
                    for (UUID skuId : skuIds) {
                        cart.add(skuId, 1, Instant.now());
                    }
                    carts.save(cart);
                });
    }

    private List<UUID> cartSkus(UUID buyerId) {
        return jdbc.sql("SELECT sku_id FROM cart_item WHERE member_id = :id")
                .param("id", buyerId)
                .query(UUID.class)
                .list();
    }

    private String withOrderId(String example, UUID orderId, @Nullable Long amount)
            throws Exception {
        ObjectNode node =
                (ObjectNode) json.readTree(Files.readString(PUBLISHER_EXAMPLES.resolve(example)));
        node.put("orderId", orderId.toString());
        if (amount != null) {
            node.put("amount", amount);
        }
        return json.writeValueAsString(node);
    }

    private JsonNode example(String type) throws Exception {
        return json.readTree(Files.readString(EVENTS.resolve(type + ".json")));
    }

    private String status(UUID orderId) {
        return jdbc.sql("SELECT status FROM orders WHERE id = :id")
                .param("id", orderId)
                .query(String.class)
                .single();
    }

    private List<String> outboxTypes(UUID orderId) {
        return jdbc.sql(
                        "SELECT type FROM outbox_event WHERE aggregate_id = :id ORDER BY occurred_at, id")
                .param("id", orderId.toString())
                .query(String.class)
                .list();
    }

    private String outboxPayload(UUID orderId, String type) {
        return jdbc.sql(
                        "SELECT payload::text FROM outbox_event WHERE aggregate_id = :id AND type = :type")
                .param("id", orderId.toString())
                .param("type", type)
                .query(String.class)
                .single();
    }

    private void send(UUID eventId, String type, String payload, UUID key) throws Exception {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(TOPIC, key.toString(), payload);
        record.headers().add("id", utf8(eventId.toString()));
        record.headers().add("type", utf8(type));
        record.headers().add("occurredAt", utf8("2026-10-21T03:04:05.123456Z"));
        record.headers().add("schemaVersion", utf8("1"));
        producer.send(record).get();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
