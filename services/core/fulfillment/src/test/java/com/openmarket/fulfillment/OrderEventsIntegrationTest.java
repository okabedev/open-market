package com.openmarket.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * order가 낸 {@code OrderPaid}(Debezium과 같은 형식) → 리스너 → 하위 주문마다 {@code READY}
 * 배송(docs/architecture/fulfillment.md "배송"). 페이로드는 발행자(order)의 JSON 예시를 읽어 ID만 바꾼다.
 */
@SpringBootTest
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    OrderEventsIntegrationTest.Topics.class
})
class OrderEventsIntegrationTest {

    static final String TOPIC = "order.order";
    static final Path PUBLISHER_EXAMPLES = Path.of("../order/src/test/resources/events");

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic orderOrder() {
            return TopicBuilder.name(TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic orderOrderDlt() {
            return TopicBuilder.name(TOPIC + ".DLT").partitions(1).build();
        }
    }

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired KafkaContainer kafka;

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
    @DisplayName("OrderPaid를 받으면 하위 주문마다 READY 배송을 만든다. 같은 이벤트나 같은 주문의 이벤트가 다시 와도 하위 주문마다 하나다")
    void createsShipments() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String payload = orderPaid(orderId, first, second);
        UUID eventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();

        send(UUID.randomUUID(), "OrderPlaced", "{\"orderId\":\"" + orderId + "\"}", orderId);
        send(eventId, "OrderPaid", payload, orderId);
        send(eventId, "OrderPaid", payload, orderId);
        send(secondEventId, "OrderPaid", payload, orderId);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.sql(
                                                                "SELECT count(*) FROM processed_event"
                                                                        + " WHERE consumer ="
                                                                        + " 'fulfillment.order-paid'"
                                                                        + " AND event_id IN (:ids)")
                                                        .param(
                                                                "ids",
                                                                List.of(eventId, secondEventId))
                                                        .query(Integer.class)
                                                        .single())
                                        .isEqualTo(2));
        assertThat(
                        jdbc.sql(
                                        "SELECT seller_order_id || ' ' || status || ' ' || amount"
                                                + " FROM shipment WHERE order_id = :orderId"
                                                + " ORDER BY amount")
                                .param("orderId", orderId)
                                .query(String.class)
                                .list())
                .containsExactly(first + " READY 19900", second + " READY 39800");
        assertThat(
                        jdbc.sql(
                                        "SELECT l.product_name || ' ' || coalesce(l.option_value_1,"
                                                + " '-') || ' ' || coalesce(l.option_value_2, '-')"
                                                + " || ' ' || l.quantity || ' ' || l.amount FROM"
                                                + " shipment_line l JOIN shipment s ON s.id ="
                                                + " l.shipment_id WHERE s.seller_order_id = :id")
                                .param("id", first)
                                .query(String.class)
                                .list())
                .containsExactly("반팔 티셔츠 화이트 M 1 19900");
    }

    @Test
    @DisplayName("주문 항목 ID가 없는 M4 이전 형식의 OrderPaid는 다시 시도하지 않고 DLT로 보낸다. 뒤따른 이벤트는 처리된다")
    void rejectsPreM4OrderPaid() throws Exception {
        UUID legacyOrderId = UUID.randomUUID();
        ObjectNode legacy =
                (ObjectNode)
                        json.readTree(
                                orderPaid(legacyOrderId, UUID.randomUUID(), UUID.randomUUID()));
        for (var sellerOrder : legacy.get("sellerOrders")) {
            for (var line : sellerOrder.get("lines")) {
                ((ObjectNode) line).remove(List.of("orderLineId", "productName", "optionValues"));
            }
        }
        UUID legacyEventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        try (KafkaConsumer<String, String> dlt = dltConsumer()) {
            send(legacyEventId, "OrderPaid", json.writeValueAsString(legacy), legacyOrderId);
            send(
                    UUID.randomUUID(),
                    "OrderPaid",
                    orderPaid(orderId, UUID.randomUUID(), UUID.randomUUID()),
                    orderId);

            await().atMost(Duration.ofSeconds(15))
                    .untilAsserted(
                            () ->
                                    assertThat(
                                                    jdbc.sql(
                                                                    "SELECT count(*) FROM shipment"
                                                                            + " WHERE order_id ="
                                                                            + " :orderId")
                                                            .param("orderId", orderId)
                                                            .query(Integer.class)
                                                            .single())
                                            .isEqualTo(2));
            List<String> dead = new ArrayList<>();
            await().atMost(Duration.ofSeconds(15))
                    .untilAsserted(
                            () -> {
                                dlt.poll(Duration.ofMillis(200))
                                        .forEach(
                                                r ->
                                                        dead.add(
                                                                header(r, "id")
                                                                        + " "
                                                                        + header(
                                                                                r,
                                                                                "kafka_dlt-exception-cause-fqcn")));
                                // 영구 오류(InvalidEventException)로 바로 보낸다. 다시 시도한
                                // NullPointerException이 아니다.
                                assertThat(dead)
                                        .contains(
                                                legacyEventId
                                                        + " com.openmarket.common.messaging"
                                                        + ".consumer.InvalidEventException");
                            });
        }
        assertThat(
                        jdbc.sql("SELECT count(*) FROM shipment WHERE order_id = :orderId")
                                .param("orderId", legacyOrderId)
                                .query(Integer.class)
                                .single())
                .isZero();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private KafkaConsumer<String, String> dltConsumer() {
        KafkaConsumer<String, String> consumer =
                new KafkaConsumer<>(
                        Map.of(
                                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                kafka.getBootstrapServers(),
                                ConsumerConfig.GROUP_ID_CONFIG,
                                "dlt-" + UUID.randomUUID(),
                                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                                "earliest"),
                        new StringDeserializer(),
                        new StringDeserializer());
        consumer.subscribe(List.of(TOPIC + ".DLT"));
        return consumer;
    }

    private String orderPaid(UUID orderId, UUID first, UUID second) throws Exception {
        ObjectNode node =
                (ObjectNode)
                        json.readTree(
                                Files.readString(PUBLISHER_EXAMPLES.resolve("OrderPaid.json")));
        node.put("orderId", orderId.toString());
        ((ObjectNode) node.get("sellerOrders").get(0)).put("sellerOrderId", first.toString());
        ((ObjectNode) node.get("sellerOrders").get(1)).put("sellerOrderId", second.toString());
        return json.writeValueAsString(node);
    }

    private void send(UUID eventId, String type, String payload, UUID key) throws Exception {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(TOPIC, key.toString(), payload);
        record.headers().add("id", eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("type", type.getBytes(StandardCharsets.UTF_8));
        record.headers().add("occurredAt", "2026-10-21T00:00:00Z".getBytes(StandardCharsets.UTF_8));
        record.headers().add("schemaVersion", "1".getBytes(StandardCharsets.UTF_8));
        producer.send(record).get();
    }
}
