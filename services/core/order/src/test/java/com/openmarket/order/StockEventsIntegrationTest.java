package com.openmarket.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
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
 * catalog가 낸 예약 결과(Debezium과 같은 형식) → 리스너 → 주문 상태(docs/architecture/order.md "예약 결과"). 페이로드는 발행자
 * (catalog)의 JSON 예시를 읽어 orderId만 바꾼다.
 */
@SpringBootTest
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    StockEventsIntegrationTest.Topics.class
})
class StockEventsIntegrationTest {

    static final String TOPIC = "catalog.stock";
    static final Path PUBLISHER_EXAMPLES = Path.of("../catalog/src/test/resources/events");
    static final Path EVENTS = Path.of("src/test/resources/events");

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic catalogStock() {
            return TopicBuilder.name(TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic catalogStockDlt() {
            return TopicBuilder.name(TOPIC + ".DLT").partitions(1).build();
        }
    }

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired KafkaContainer kafka;

    @Autowired OrderRepository orders;

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
    @DisplayName("StockReserved를 받으면 결제 대기가 되고 OrderAwaitingPayment를 낸다. 같은 이벤트가 다시 와도 한 번만 낸다")
    void awaitsPayment() throws Exception {
        UUID orderId = pendingOrder();
        UUID eventId = UUID.randomUUID();

        send(eventId, "StockReserved", withOrderId("StockReserved.json", orderId), orderId);
        send(eventId, "StockReserved", withOrderId("StockReserved.json", orderId), orderId);
        send(
                UUID.randomUUID(),
                "StockAvailabilityChanged",
                Files.readString(PUBLISHER_EXAMPLES.resolve("StockAvailabilityChanged.json")),
                UUID.randomUUID());

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(status(orderId)).isEqualTo("AWAITING_PAYMENT"));
        assertThat(outboxTypes(orderId)).containsExactly("OrderPlaced", "OrderAwaitingPayment");
        JsonNode awaiting = json.readTree(outboxPayload(orderId, "OrderAwaitingPayment"));
        assertThat(awaiting.propertyNames())
                .isEqualTo(example("OrderAwaitingPayment").propertyNames());
        assertThat(awaiting.get("amount").asLong()).isEqualTo(59_700);
        assertThat(awaiting.get("orderName").asString()).isEqualTo("반팔 티셔츠");
    }

    @Test
    @DisplayName("StockReservationFailed를 받으면 STOCK_UNAVAILABLE로 취소하고 OrderCancelled를 낸다")
    void cancelsOnFailure() throws Exception {
        UUID orderId = pendingOrder();

        send(
                UUID.randomUUID(),
                "StockReservationFailed",
                withOrderId("StockReservationFailed.json", orderId),
                orderId);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(status(orderId)).isEqualTo("CANCELLED"));
        assertThat(
                        jdbc.sql("SELECT cancel_reason FROM orders WHERE id = :id")
                                .param("id", orderId)
                                .query(String.class)
                                .single())
                .isEqualTo("STOCK_UNAVAILABLE");
        JsonNode cancelled = json.readTree(outboxPayload(orderId, "OrderCancelled"));
        assertThat(cancelled.propertyNames()).isEqualTo(example("OrderCancelled").propertyNames());
        assertThat(cancelled.get("reason").asString()).isEqualTo("STOCK_UNAVAILABLE");
    }

    /** PENDING 주문을 저장하고 OrderPlaced를 outbox에 넣는다(주문 생성 API와 같은 결과). */
    private UUID pendingOrder() {
        Order order =
                Order.place(
                        UUID.randomUUID(),
                        List.of(
                                new OrderItem(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        "반팔 티셔츠",
                                        UUID.randomUUID(),
                                        "길동상회",
                                        UUID.randomUUID(),
                                        List.of(),
                                        List.of(),
                                        19_900,
                                        3)),
                        ShippingAddress.of("홍길동", "010-1234-5678", "06236", "주소", null),
                        Instant.now());
        transaction.executeWithoutResult(status -> orders.save(order));
        jdbc.sql(
                        "INSERT INTO outbox_event (id, aggregate_type, aggregate_id, type, payload,"
                                + " schema_version, occurred_at) VALUES (:id, 'order.order', :orderId,"
                                + " 'OrderPlaced', '{}'::jsonb, 1, now())")
                .param("id", UUID.randomUUID())
                .param("orderId", order.id().toString())
                .update();
        return order.id();
    }

    private String withOrderId(String example, UUID orderId) throws Exception {
        ObjectNode node =
                (ObjectNode) json.readTree(Files.readString(PUBLISHER_EXAMPLES.resolve(example)));
        node.put("orderId", orderId.toString());
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
        record.headers().add("occurredAt", utf8("2026-10-20T03:04:05.123456Z"));
        record.headers().add("schemaVersion", utf8("1"));
        producer.send(record).get();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
