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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * fulfillment가 낸 배송 이벤트(Debezium과 같은 형식) → 리스너 → 하위 주문 진행 상태(docs/architecture/order.md "하위 주문 진행
 * 상태"). 페이로드는 발행자(fulfillment)의 JSON 예시를 읽어 ID만 바꾼다.
 */
@SpringBootTest
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    ShipmentEventsIntegrationTest.Topics.class
})
class ShipmentEventsIntegrationTest {

    static final String TOPIC = "fulfillment.shipment";
    static final Path PUBLISHER_EXAMPLES = Path.of("../fulfillment/src/test/resources/events");

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic fulfillmentShipment() {
            return TopicBuilder.name(TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic fulfillmentShipmentDlt() {
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
    @DisplayName("배송 이벤트와 구매확정마다 하위 주문 진행 상태가 앞으로 간다. 늦게 온 앞 단계 이벤트와 같은 이벤트는 바꾸지 않는다")
    void advances() throws Exception {
        Order order = paidOrder();
        UUID sellerOrderId = order.sellerOrders().getFirst().id();
        assertThat(progress(sellerOrderId)).isEqualTo("PAID");

        UUID shipped = UUID.randomUUID();
        send(UUID.randomUUID(), "ShipmentAccepted", order, sellerOrderId);
        send(shipped, "ShipmentShipped", order, sellerOrderId);
        send(shipped, "ShipmentShipped", order, sellerOrderId);
        send(UUID.randomUUID(), "ShipmentInTransit", order, sellerOrderId);
        send(UUID.randomUUID(), "ShipmentAccepted", order, sellerOrderId);
        send(UUID.randomUUID(), "ShipmentDelivered", order, sellerOrderId);
        UUID confirmed = UUID.randomUUID();
        send(confirmed, "PurchaseConfirmed", order, sellerOrderId);
        UUID delivered = UUID.randomUUID();
        send(delivered, "ShipmentDelivered", order, sellerOrderId);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.sql(
                                                                "SELECT count(*) FROM processed_event"
                                                                        + " WHERE event_id = :id")
                                                        .param("id", delivered)
                                                        .query(Integer.class)
                                                        .single())
                                        .isEqualTo(1));
        assertThat(progress(sellerOrderId)).isEqualTo("CONFIRMED");
        assertThat(
                        jdbc.sql("SELECT count(*) FROM outbox_event WHERE aggregate_id =" + " :id")
                                .param("id", order.id().toString())
                                .query(Integer.class)
                                .single())
                .isZero();
    }

    private Order paidOrder() {
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
                                        1)),
                        ShippingAddress.of("홍길동", "010-1234-5678", "06236", "주소", null),
                        Instant.now());
        order.recordReservation(Order.Reservation.STOCK, Instant.now());
        order.pay(19_900, Instant.now(), Instant.now());
        transaction.executeWithoutResult(status -> orders.save(order));
        return order;
    }

    private String progress(UUID sellerOrderId) {
        return jdbc.sql("SELECT progress FROM seller_order WHERE id = :id")
                .param("id", sellerOrderId)
                .query(String.class)
                .single();
    }

    private void send(UUID eventId, String type, Order order, UUID sellerOrderId) throws Exception {
        ObjectNode payload =
                (ObjectNode)
                        json.readTree(Files.readString(PUBLISHER_EXAMPLES.resolve(type + ".json")));
        payload.put("orderId", order.id().toString());
        payload.put("sellerOrderId", sellerOrderId.toString());
        ProducerRecord<String, String> record =
                new ProducerRecord<>(
                        TOPIC, sellerOrderId.toString(), json.writeValueAsString(payload));
        record.headers().add("id", eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("type", type.getBytes(StandardCharsets.UTF_8));
        record.headers().add("occurredAt", "2026-10-21T00:00:00Z".getBytes(StandardCharsets.UTF_8));
        record.headers().add("schemaVersion", "1".getBytes(StandardCharsets.UTF_8));
        producer.send(record).get();
    }
}
