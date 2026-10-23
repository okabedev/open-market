package com.openmarket.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.test.container.RedisContainerConfiguration;
import com.openmarket.promotion.command.application.CreateCouponCommand;
import com.openmarket.promotion.command.application.CreateCouponUseCase;
import com.openmarket.promotion.command.application.IssueCouponCommand;
import com.openmarket.promotion.command.application.IssueCouponUseCase;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * order가 낸 주문 이벤트(Debezium과 같은 형식) → 리스너 → 쿠폰 예약·사용·복원(docs/architecture/promotion.md "주문 쿠폰
 * 예약·사용·복원"). 페이로드는 발행자(order)의 JSON 예시를 읽어 ID만 바꾼다.
 */
@SpringBootTest
@Import({
    PostgresContainerConfiguration.class,
    RedisContainerConfiguration.class,
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

    @Autowired CreateCouponUseCase create;

    @Autowired IssueCouponUseCase issue;

    KafkaProducer<String, String> producer;
    UUID buyerId;
    UUID couponId;

    @BeforeEach
    void setUp() {
        producer =
                new KafkaProducer<>(
                        Map.of(
                                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                kafka.getBootstrapServers()),
                        new StringSerializer(),
                        new StringSerializer());
        buyerId = UUID.randomUUID();
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        couponId =
                create.execute(
                        new CreateCouponCommand(
                                UUID.randomUUID(),
                                null,
                                "3천 원",
                                CreateCouponCommand.DiscountType.FIXED,
                                3_000,
                                null,
                                0,
                                100,
                                start,
                                start.plus(7, ChronoUnit.DAYS),
                                7));
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    @DisplayName("OrderPlaced를 받으면 쿠폰을 묶고 CouponReserved를 낸다. 같은 이벤트가 다시 와도 한 번만 처리한다")
    void reservesOnOrderPlaced() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID issued = issue(buyerId);
        UUID eventId = UUID.randomUUID();

        send(eventId, "OrderPlaced", placed(orderId, buyerId, List.of(issued)), orderId);
        send(eventId, "OrderPlaced", placed(orderId, buyerId, List.of(issued)), orderId);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(status(issued)).isEqualTo("RESERVED"));
        assertThat(boundOrder(issued)).isEqualTo(orderId);
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () -> assertThat(processed(eventId, "promotion.order-placed")).isTrue());
        assertThat(outboxTypes(orderId)).containsExactly("CouponReserved");
        assertThat(json.readTree(outboxPayload(orderId, "CouponReserved")).propertyNames())
                .isEqualTo(example("CouponReserved").propertyNames());
    }

    @Test
    @DisplayName("남의 쿠폰이 섞이면 아무것도 묶지 않고 CouponReservationFailed를 낸다")
    void failsOnUnavailableCoupon() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID mine = issue(buyerId);
        UUID others = issue(UUID.randomUUID());

        send(
                UUID.randomUUID(),
                "OrderPlaced",
                placed(orderId, buyerId, List.of(mine, others)),
                orderId);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertThat(outboxTypes(orderId))
                                        .containsExactly("CouponReservationFailed"));
        assertThat(status(mine)).isEqualTo("AVAILABLE");
        JsonNode payload = json.readTree(outboxPayload(orderId, "CouponReservationFailed"));
        assertThat(payload.propertyNames())
                .isEqualTo(example("CouponReservationFailed").propertyNames());
        assertThat(payload.get("reason").asString()).isEqualTo("COUPON_UNAVAILABLE");
        assertThat(payload.get("issuedCouponIds").get(0).asString()).isEqualTo(others.toString());
    }

    @Test
    @DisplayName("OrderPaid를 받으면 묶인 쿠폰을 쓰고, OrderCancelled를 받으면 되돌린다")
    void usesAndRestores() throws Exception {
        UUID paidOrder = UUID.randomUUID();
        UUID paid = issue(buyerId);
        send(
                UUID.randomUUID(),
                "OrderPlaced",
                placed(paidOrder, buyerId, List.of(paid)),
                paidOrder);
        send(UUID.randomUUID(), "OrderPaid", withOrderId("OrderPaid", paidOrder), paidOrder);

        UUID cancelledOrder = UUID.randomUUID();
        UUID otherBuyer = UUID.randomUUID();
        UUID cancelled = issue(otherBuyer);
        send(
                UUID.randomUUID(),
                "OrderPlaced",
                placed(cancelledOrder, otherBuyer, List.of(cancelled)),
                cancelledOrder);
        send(
                UUID.randomUUID(),
                "OrderCancelled",
                withOrderId("OrderCancelled", cancelledOrder),
                cancelledOrder);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () -> {
                            assertThat(status(paid)).isEqualTo("USED");
                            assertThat(status(cancelled)).isEqualTo("AVAILABLE");
                        });
        assertThat(boundOrder(cancelled)).isNull();
    }

    @Test
    @DisplayName("쿠폰이 없는 OrderPlaced는 처리 기록만 남기고 이벤트를 내지 않는다")
    void skipsOrderWithoutCoupons() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        send(eventId, "OrderPlaced", placed(orderId, buyerId, List.of()), orderId);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () -> assertThat(processed(eventId, "promotion.order-placed")).isTrue());
        assertThat(outboxTypes(orderId)).isEmpty();
    }

    private UUID issue(UUID memberId) {
        return issue.execute(new IssueCouponCommand(memberId, couponId)).issuedCouponId();
    }

    /** 발행자 예시를 읽어 orderId, buyerId, 쿠폰을 바꾼다. 항목은 예시 그대로 둔다. */
    private String placed(UUID orderId, UUID buyer, List<UUID> issuedCouponIds) throws IOException {
        ObjectNode node =
                (ObjectNode)
                        json.readTree(
                                Files.readString(PUBLISHER_EXAMPLES.resolve("OrderPlaced.json")));
        node.put("orderId", orderId.toString());
        node.put("buyerId", buyer.toString());
        ArrayNode coupons = node.putArray("issuedCouponIds");
        issuedCouponIds.forEach(id -> coupons.add(id.toString()));
        return json.writeValueAsString(node);
    }

    /** promotion은 orderId만 읽는다. 나머지 필드는 예시 그대로 두어 역직렬화가 깨지지 않는지 본다. */
    private String withOrderId(String type, UUID orderId) throws IOException {
        ObjectNode node =
                (ObjectNode)
                        json.readTree(Files.readString(PUBLISHER_EXAMPLES.resolve(type + ".json")));
        node.put("orderId", orderId.toString());
        return json.writeValueAsString(node);
    }

    private JsonNode example(String type) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/events/" + type + ".json")) {
            return json.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private String status(UUID issuedCouponId) {
        return jdbc.sql("SELECT status FROM issued_coupon WHERE id = :id")
                .param("id", issuedCouponId)
                .query(String.class)
                .single();
    }

    private @Nullable UUID boundOrder(UUID issuedCouponId) {
        return jdbc.sql("SELECT order_id FROM issued_coupon WHERE id = :id")
                .param("id", issuedCouponId)
                .query(UUID.class)
                .optional()
                .orElse(null);
    }

    private List<String> outboxTypes(UUID orderId) {
        return jdbc.sql(
                        "SELECT type FROM outbox_event WHERE aggregate_type = 'promotion.issued-coupon'"
                                + " AND aggregate_id = :id ORDER BY occurred_at, id")
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

    private boolean processed(UUID eventId, String consumer) {
        return jdbc.sql(
                                "SELECT count(*) FROM processed_event WHERE event_id = :eventId"
                                        + " AND consumer = :consumer")
                        .param("eventId", eventId)
                        .param("consumer", consumer)
                        .query(Integer.class)
                        .single()
                == 1;
    }

    private void send(UUID eventId, String type, String payload, UUID key) throws Exception {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(TOPIC, key.toString(), payload);
        record.headers().add("id", utf8(eventId.toString()));
        record.headers().add("type", utf8(type));
        record.headers().add("occurredAt", utf8("2026-10-23T03:04:05.123456Z"));
        record.headers().add("schemaVersion", utf8("1"));
        producer.send(record).get();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
