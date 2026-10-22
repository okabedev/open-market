package com.openmarket.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.kafka.KafkaContainer;

/**
 * 원인 이벤트 → 인앱 알림(docs/architecture/notification.md "인앱 알림"). 페이로드는 발행자의 JSON 예시를 그대로 쓴다
 * (docs/architecture/messaging.md "스키마 규칙").
 */
@SpringBootTest
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    NotificationTopics.class
})
class InAppEventsIntegrationTest {

    static final Path CORE = Path.of("../../core");

    @Autowired JdbcClient jdbc;

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
    @DisplayName("OrderPaid는 구매자에게 주문 완료, 하위 주문의 판매자마다 새 주문 알림을 만든다. 같은 이벤트가 다시 와도 그대로다")
    void orderPaid() throws Exception {
        UUID eventId = UUID.randomUUID();

        send("order.order", eventId, "OrderPaid", example("order", "OrderPaid"));
        send("order.order", eventId, "OrderPaid", example("order", "OrderPaid"));
        send("order.order", UUID.randomUUID(), "OrderPlaced", example("order", "OrderPlaced"));

        List<String> rows = awaitRows(eventId, 3);
        assertThat(rows)
                .containsExactlyInAnyOrder(
                        "ORDER_PAID BUYER 019a2c3d-1a2b-7c3d-8e4f-5a6b7c8d9e0f ORDER"
                                + " 019a2c3d-4e5f-7a60-8b1c-2d3e4f5a6b70 | 주문이 완료됐습니다. 반팔 티셔츠 외 1건,"
                                + " 59,700원",
                        "SELLER_ORDER_RECEIVED SELLER 019a2b00-0000-7000-8000-00000000a001"
                                + " SELLER_ORDER 019a2c3d-4e60-7b71-8c2d-3e4f5a6b7c81 | 새 주문이 들어왔습니다."
                                + " 반팔 티셔츠, 19,900원. 발주를 확인해 주세요.",
                        "SELLER_ORDER_RECEIVED SELLER 019a2b00-0000-7000-8000-00000000a002"
                                + " SELLER_ORDER 019a2c3d-4e61-7c82-8d3e-4f5a6b7c8d92 | 새 주문이 들어왔습니다. 면"
                                + " 양말 3켤레, 39,800원. 발주를 확인해 주세요.");
        UUID later = UUID.randomUUID();
        send("order.order", later, "OrderPaid", example("order", "OrderPaid"));
        awaitRows(later, 3);
        assertThat(rows(eventId)).as("파티션 하나라 다음 이벤트까지 처리했으면 중복도 처리했다").hasSize(3);
    }

    @Test
    @DisplayName("배송·클레임·환불·입점·제재 이벤트마다 받는 쪽에 알맞은 알림을 하나씩 만든다")
    void otherEvents() throws Exception {
        Map<String, String> expected =
                Map.ofEntries(
                        Map.entry(
                                "fulfillment.shipment/ShipmentShipped",
                                "SHIPMENT_SHIPPED BUYER 019a2c3d-1a2b-7c3d-8e4f-5a6b7c8d9e0f ORDER"),
                        Map.entry(
                                "fulfillment.shipment/ShipmentDelivered",
                                "SHIPMENT_DELIVERED BUYER 019a2c3d-1a2b-7c3d-8e4f-5a6b7c8d9e0f ORDER"),
                        Map.entry(
                                "fulfillment.claim/ClaimRequested",
                                "CLAIM_REQUESTED SELLER 019a2b00-0000-7000-8000-00000000a001 CLAIM"),
                        Map.entry(
                                "fulfillment.claim/ClaimApproved",
                                "CLAIM_APPROVED BUYER 019a2c3d-1a2b-7c3d-8e4f-5a6b7c8d9e0f ORDER"),
                        Map.entry(
                                "fulfillment.claim/ClaimRejected",
                                "CLAIM_REJECTED BUYER 019a2c3d-1a2b-7c3d-8e4f-5a6b7c8d9e0f ORDER"),
                        Map.entry(
                                "fulfillment.claim/ClaimCompleted",
                                "CLAIM_COMPLETED BUYER 019a2c3d-1a2b-7c3d-8e4f-5a6b7c8d9e0f ORDER"),
                        Map.entry(
                                "fulfillment.claim/ClaimMediationRequested",
                                "CLAIM_MEDIATION_REQUESTED ADMIN - CLAIM"),
                        Map.entry("payment.payment/RefundFailed", "REFUND_FAILED ADMIN - REFUND"),
                        Map.entry(
                                "member.seller/SellerApproved",
                                "SELLER_APPROVED SELLER 019a0f3c-5e2b-7c41-9d3e-2f6a8b1c4d70 -"),
                        Map.entry(
                                "member.seller/SellerRejected",
                                "SELLER_REJECTED BUYER 019a0f3c-5e2b-7c41-9d3e-2f6a8b1c4d70 SELLER_APPLICATION"),
                        Map.entry(
                                "catalog.product/ProductBanned",
                                "PRODUCT_BANNED SELLER 019a0f3c-5e2b-7c41-9d3e-2f6a8b1c4d70 PRODUCT"));
        Map<String, String> publishers =
                Map.of(
                        "fulfillment.shipment", "fulfillment",
                        "fulfillment.claim", "fulfillment",
                        "payment.payment", "payment",
                        "member.seller", "member",
                        "catalog.product", "catalog");
        Map<String, UUID> eventIds = new HashMap<>();
        for (String key : expected.keySet()) {
            String topic = key.substring(0, key.indexOf('/'));
            String type = key.substring(key.indexOf('/') + 1);
            UUID eventId = UUID.randomUUID();
            eventIds.put(key, eventId);
            send(topic, eventId, type, example(publishers.get(topic), type));
        }

        for (Map.Entry<String, String> entry : expected.entrySet()) {
            List<String> rows = awaitRows(eventIds.get(entry.getKey()), 1);
            assertThat(rows.getFirst()).as(entry.getKey()).startsWith(entry.getValue() + " ");
        }
        assertThat(rows(eventIds.get("fulfillment.claim/ClaimRejected")).getFirst())
                .endsWith("| 반품 신청이 거부됐습니다. 거부 사유를 확인하고 중재를 요청할 수 있습니다.");
        assertThat(rows(eventIds.get("payment.payment/RefundFailed")).getFirst())
                .endsWith("| 환불이 실패했습니다(NOT_CANCELABLE_AMOUNT). 환불 재시도 화면에서 확인해 주세요.");
    }

    @Test
    @DisplayName("발송 전 취소의 ClaimApproved는 알리지 않고 처리 기록만 남긴다")
    void cancelApprovalIsSilent() throws Exception {
        String approved =
                example("fulfillment", "ClaimApproved").replace("\"RETURN\"", "\"CANCEL\"");
        UUID eventId = UUID.randomUUID();

        send("fulfillment.claim", eventId, "ClaimApproved", approved);

        await().atMost(Duration.ofSeconds(30))
                .until(
                        () ->
                                jdbc.sql(
                                                        "SELECT count(*) FROM processed_event WHERE event_id = :id")
                                                .param("id", eventId)
                                                .query(Integer.class)
                                                .single()
                                        == 1);
        assertThat(rows(eventId)).isEmpty();
    }

    private List<String> awaitRows(UUID eventId, int count) {
        await().atMost(Duration.ofSeconds(30)).until(() -> rows(eventId).size() >= count);
        return rows(eventId);
    }

    private List<String> rows(UUID eventId) {
        return jdbc.sql(
                        """
                        SELECT kind || ' ' || audience || ' ' || coalesce(recipient_account_id::text, '-')
                               || ' ' || coalesce(target_type, '-')
                               || coalesce(' ' || target_id::text, '') || ' | ' || message
                        FROM notification
                        WHERE source_event_id = :id AND channel = 'IN_APP' AND status = 'SENT'
                        """)
                .param("id", eventId)
                .query(String.class)
                .list();
    }

    private static String example(String publisher, String type) throws Exception {
        return Files.readString(
                CORE.resolve(publisher).resolve("src/test/resources/events/" + type + ".json"));
    }

    private void send(String topic, UUID eventId, String type, String payload) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, "key", payload);
        record.headers().add("id", utf8(eventId.toString()));
        record.headers().add("type", utf8(type));
        record.headers().add("occurredAt", utf8("2026-10-22T03:04:05.123456Z"));
        record.headers().add("schemaVersion", utf8("1"));
        producer.send(record).get();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
