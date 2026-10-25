package com.openmarket.settlement;

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
import tools.jackson.databind.node.ObjectNode;

/**
 * fulfillment가 낸 {@code PurchaseConfirmed}(Debezium과 같은 형식) → 리스너 → 정산
 * 대상(docs/architecture/settlement.md "정산 대상"). 페이로드는 발행자(fulfillment)의 JSON 예시를 읽어 ID만 바꾼다.
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
    @DisplayName("PurchaseConfirmed를 받으면 하위 주문마다 정산 대상을 만든다. 같은 하위 주문이 다시 와도 하나다")
    void createsItem() throws Exception {
        UUID sellerOrderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID otherEventId = UUID.randomUUID();
        String payload = confirmed(sellerOrderId);

        send(eventId, "PurchaseConfirmed", payload);
        send(eventId, "PurchaseConfirmed", payload);
        // 다른 이벤트 ID로 다시 와도 하위 주문마다 하나다.
        send(otherEventId, "PurchaseConfirmed", payload);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () -> {
                            assertThat(processed(eventId)).isTrue();
                            assertThat(processed(otherEventId)).isTrue();
                        });
        // 예시: 19,900원 1개, 요율 없는 카테고리(기본 10%), 판매자 쿠폰 2,000원
        assertThat(
                        jdbc.sql(
                                        "SELECT sales_amount || '/' || commission_amount || '/' ||"
                                                + " seller_coupon_discount || '/' || settlement_amount"
                                                + " FROM settlement_item WHERE seller_order_id = :id")
                                .param("id", sellerOrderId)
                                .query(String.class)
                                .list())
                .containsExactly("19900/1990/2000/15910");
        // 2026-10-28T07:00Z(구매확정)는 2026-10-26(월) 00:00 KST부터의 기간이다
        assertThat(
                        jdbc.sql(
                                        "SELECT period_start = timestamptz '2026-10-26T00:00:00+09:00'"
                                                + " AND period_end = timestamptz"
                                                + " '2026-11-02T00:00:00+09:00' FROM settlement_item"
                                                + " WHERE seller_order_id = :id")
                                .param("id", sellerOrderId)
                                .query(Boolean.class)
                                .single())
                .isTrue();
        assertThat(
                        jdbc.sql(
                                        "SELECT l.product_name || '/' || l.category_id || '/' ||"
                                                + " l.quantity || '/' || l.commission_rate_bp FROM"
                                                + " settlement_item_line l JOIN settlement_item i ON"
                                                + " i.id = l.settlement_item_id WHERE"
                                                + " i.seller_order_id = :id")
                                .param("id", sellerOrderId)
                                .query(String.class)
                                .list())
                .containsExactly("반팔 티셔츠/0199f000-0000-7000-8000-000000000101/1/1000");
    }

    @Test
    @DisplayName("같은 토픽의 다른 이벤트는 처리 기록 없이 넘긴다")
    void skipsOtherEvents() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID sellerOrderId = UUID.randomUUID();
        send(eventId, "ShipmentDelivered", confirmed(sellerOrderId));
        // 뒤에 보낸 이벤트가 처리되면 앞의 이벤트도 지나갔다(파티션 하나).
        UUID marker = UUID.randomUUID();
        send(UUID.randomUUID(), "PurchaseConfirmed", confirmed(marker));

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(items(marker)).isEqualTo(1));
        assertThat(items(sellerOrderId)).isZero();
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
                                        + " AND consumer = 'settlement.purchase-confirmed'")
                        .param("eventId", eventId)
                        .query(Integer.class)
                        .single()
                == 1;
    }

    private int items(UUID sellerOrderId) {
        return jdbc.sql("SELECT count(*) FROM settlement_item WHERE seller_order_id = :id")
                .param("id", sellerOrderId)
                .query(Integer.class)
                .single();
    }

    /** 발행자 예시를 읽어 하위 주문 ID를 바꾼다. 나머지는 예시 그대로 두어 역직렬화가 깨지지 않는지 본다. */
    private String confirmed(UUID sellerOrderId) throws IOException {
        ObjectNode node =
                (ObjectNode)
                        json.readTree(
                                Files.readString(
                                        PUBLISHER_EXAMPLES.resolve("PurchaseConfirmed.json")));
        node.put("sellerOrderId", sellerOrderId.toString());
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
