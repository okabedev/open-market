package com.openmarket.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * fulfillment가 낸 {@code PurchaseConfirmed}(Debezium과 같은 형식) → 리스너 → 리뷰 작성
 * 자격(docs/architecture/review.md "리뷰 작성 자격"). 페이로드는 발행자(fulfillment)의 JSON 예시를 읽어 ID만 바꾼다.
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
    @DisplayName("PurchaseConfirmed를 받으면 주문 항목마다 작성 자격을 만든다. 같은 이벤트가 다시 와도 한 번만 처리한다")
    void createsEligibility() throws Exception {
        UUID buyerId = UUID.randomUUID();
        UUID line = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String payload = confirmed(buyerId, line);

        UUID otherEventId = UUID.randomUUID();
        send(eventId, "PurchaseConfirmed", payload);
        send(eventId, "PurchaseConfirmed", payload);
        // 다른 이벤트 ID로 다시 와도 주문 항목마다 하나다.
        send(otherEventId, "PurchaseConfirmed", payload);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () -> {
                            assertThat(processed(eventId)).isTrue();
                            assertThat(processed(otherEventId)).isTrue();
                        });
        assertThat(
                        jdbc.sql(
                                        "SELECT order_line_id || '/' || product_name || '/' ||"
                                                + " option_value_1 || '/' || option_value_2 FROM"
                                                + " review_eligibility WHERE buyer_id = :buyerId")
                                .param("buyerId", buyerId)
                                .query(String.class)
                                .list())
                .containsExactly(line + "/반팔 티셔츠/화이트/M");
        assertThat(
                        jdbc.sql(
                                        "SELECT confirmed_at = timestamptz '2026-10-28T07:00:00Z' FROM"
                                                + " review_eligibility WHERE buyer_id = :buyerId")
                                .param("buyerId", buyerId)
                                .query(Boolean.class)
                                .single())
                .isTrue();
    }

    @Test
    @DisplayName("같은 토픽의 다른 이벤트는 처리 기록 없이 넘긴다")
    void skipsOtherEvents() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        send(eventId, "ShipmentDelivered", confirmed(buyerId, UUID.randomUUID()));
        // 뒤에 보낸 이벤트가 처리되면 앞의 이벤트도 지나갔다(파티션 하나).
        UUID marker = UUID.randomUUID();
        UUID markerBuyer = UUID.randomUUID();
        send(marker, "PurchaseConfirmed", confirmed(markerBuyer, UUID.randomUUID()));

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(eligibilities(markerBuyer)).isEqualTo(1));
        assertThat(eligibilities(buyerId)).isZero();
        assertThat(
                        jdbc.sql("SELECT count(*) FROM processed_event WHERE event_id = :id")
                                .param("id", eventId)
                                .query(Integer.class)
                                .single())
                .isZero();
    }

    private boolean processed(UUID eventId) {
        return jdbc.sql(
                                "SELECT count(*) FROM processed_event WHERE event_id = :eventId"
                                        + " AND consumer = 'review.purchase-confirmed'")
                        .param("eventId", eventId)
                        .query(Integer.class)
                        .single()
                == 1;
    }

    private int eligibilities(UUID buyerId) {
        return jdbc.sql("SELECT count(*) FROM review_eligibility WHERE buyer_id = :buyerId")
                .param("buyerId", buyerId)
                .query(Integer.class)
                .single();
    }

    /** 발행자 예시를 읽어 구매자와 주문 항목 ID를 바꾼다. 나머지는 예시 그대로 두어 역직렬화가 깨지지 않는지 본다. */
    private String confirmed(UUID buyerId, UUID orderLineId) throws IOException {
        ObjectNode node =
                (ObjectNode)
                        json.readTree(
                                Files.readString(
                                        PUBLISHER_EXAMPLES.resolve("PurchaseConfirmed.json")));
        node.put("buyerId", buyerId.toString());
        ArrayNode lines = (ArrayNode) node.get("lines");
        ((ObjectNode) lines.get(0)).put("orderLineId", orderLineId.toString());
        return json.writeValueAsString(node);
    }

    private void send(UUID eventId, String type, String payload) throws Exception {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(TOPIC, UUID.randomUUID().toString(), payload);
        record.headers().add("id", utf8(eventId.toString()));
        record.headers().add("type", utf8(type));
        record.headers().add("occurredAt", utf8("2026-10-28T07:00:00.123456Z"));
        record.headers().add("schemaVersion", utf8("1"));
        producer.send(record).get();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
