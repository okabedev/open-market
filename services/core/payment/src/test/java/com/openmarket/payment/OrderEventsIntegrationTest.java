package com.openmarket.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * order가 낸 {@code OrderAwaitingPayment}(Debezium과 같은 형식) → 리스너 → {@code READY}
 * 결제(docs/architecture/payment.md "결제 준비"). 페이로드는 발행자(order)의 JSON 예시를 읽어 orderId만 바꾼다.
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
    @DisplayName("OrderAwaitingPayment를 받으면 READY 결제를 만든다. 같은 이벤트나 같은 주문의 이벤트가 다시 와도 하나다")
    void prepares() throws Exception {
        UUID orderId = UUID.randomUUID();
        String payload = withOrderId("OrderAwaitingPayment.json", orderId);
        UUID eventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();

        send(UUID.randomUUID(), "OrderPlaced", "{\"orderId\":\"" + orderId + "\"}", orderId);
        send(eventId, "OrderAwaitingPayment", payload, orderId);
        send(eventId, "OrderAwaitingPayment", payload, orderId);
        send(secondEventId, "OrderAwaitingPayment", payload, orderId);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.sql(
                                                                "SELECT status || ' ' || amount || ' ' ||"
                                                                        + " buyer_id || ' ' || order_name"
                                                                        + " FROM payment WHERE"
                                                                        + " order_id = :orderId")
                                                        .param("orderId", orderId)
                                                        .query(String.class)
                                                        .list())
                                        .containsExactly(
                                                "READY 59700 019a2c3d-1a2b-7c3d-8e4f-5a6b7c8d9e0f"
                                                        + " 반팔 티셔츠 외 1건"));
        // 뒤의 이벤트까지 처리된 뒤에도 하나다.
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.sql(
                                                                "SELECT count(*) FROM processed_event"
                                                                        + " WHERE consumer ="
                                                                        + " 'payment.order-awaiting-payment'"
                                                                        + " AND event_id IN (:ids)")
                                                        .param(
                                                                "ids",
                                                                List.of(eventId, secondEventId))
                                                        .query(Integer.class)
                                                        .single())
                                        .isEqualTo(2));
        assertThat(
                        jdbc.sql("SELECT count(*) FROM payment WHERE order_id = :orderId")
                                .param("orderId", orderId)
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);
    }

    private String withOrderId(String example, UUID orderId) throws Exception {
        ObjectNode node =
                (ObjectNode) json.readTree(Files.readString(PUBLISHER_EXAMPLES.resolve(example)));
        node.put("orderId", orderId.toString());
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
