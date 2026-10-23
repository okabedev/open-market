package com.openmarket.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.order.command.application.AwaitPaymentCommand;
import com.openmarket.order.command.application.AwaitPaymentUseCase;
import com.openmarket.order.command.application.CancelOrderCommand;
import com.openmarket.order.command.application.CancelOrderUseCase;
import com.openmarket.order.command.domain.Order;
import com.openmarket.order.command.domain.OrderItem;
import com.openmarket.order.command.domain.OrderRepository;
import com.openmarket.order.command.domain.SellerOrderDiscount;
import com.openmarket.order.command.domain.ShippingAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * 재고·쿠폰 예약 결과의 합류(R12, docs/architecture/order.md "예약 결과"). 두 결과의 순서, 실패 조합, 중복, 동시 도착마다 주문 상태가 하나로
 * 정해지고 주문 이벤트가 한 번만 나가는지 본다. 페이로드는 발행자(catalog, promotion)의 JSON 예시를 읽어 orderId만 바꾼다. 받은 예약을 푸는 일은
 * catalog·promotion이 {@code OrderCancelled}로 하고 각 서비스 테스트가 확인한다.
 */
@SpringBootTest
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    ReservationJoinIntegrationTest.Topics.class
})
class ReservationJoinIntegrationTest {

    static final String STOCK_TOPIC = "catalog.stock";
    static final String COUPON_TOPIC = "promotion.issued-coupon";
    static final Path CATALOG_EXAMPLES = Path.of("../catalog/src/test/resources/events");
    static final Path PROMOTION_EXAMPLES = Path.of("../promotion/src/test/resources/events");

    /** 주문 금액 59,700원에서 플랫폼 쿠폰 3,000원을 뺀 결제 금액. */
    static final long PAYMENT_AMOUNT = 56_700;

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic catalogStock() {
            return TopicBuilder.name(STOCK_TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic catalogStockDlt() {
            return TopicBuilder.name(STOCK_TOPIC + ".DLT").partitions(1).build();
        }

        @Bean
        NewTopic promotionIssuedCoupon() {
            return TopicBuilder.name(COUPON_TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic promotionIssuedCouponDlt() {
            return TopicBuilder.name(COUPON_TOPIC + ".DLT").partitions(1).build();
        }
    }

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired KafkaContainer kafka;

    @Autowired OrderRepository orders;

    @Autowired TransactionTemplate transaction;

    @Autowired AwaitPaymentUseCase awaitPayment;

    @Autowired CancelOrderUseCase cancel;

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
    @DisplayName("재고 결과가 먼저 와도 쿠폰 결과를 받아야 결제 대기가 되고, 결제 금액으로 OrderAwaitingPayment를 한 번 낸다")
    void stockFirst() throws Exception {
        UUID orderId = couponOrder();

        UUID stockEvent = stock("StockReserved", orderId);
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(processed(stockEvent)).isTrue());
        assertThat(reserved(orderId, "stock_reserved")).isTrue();
        assertThat(status(orderId)).isEqualTo("PENDING");
        assertThat(outboxTypes(orderId)).containsExactly("OrderPlaced");

        coupon("CouponReserved", orderId);

        awaitStatus(orderId, "AWAITING_PAYMENT");
        assertThat(outboxTypes(orderId)).containsExactly("OrderPlaced", "OrderAwaitingPayment");
        assertThat(
                        json.readTree(outboxPayload(orderId, "OrderAwaitingPayment"))
                                .get("amount")
                                .asLong())
                .isEqualTo(PAYMENT_AMOUNT);
    }

    @Test
    @DisplayName("쿠폰 결과가 먼저 와도 같다. 같은 결과를 다시 받아도 한 번만 낸다")
    void couponFirstWithDuplicates() throws Exception {
        UUID orderId = couponOrder();
        UUID couponEvent = UUID.randomUUID();
        UUID stockEvent = UUID.randomUUID();

        send(
                COUPON_TOPIC,
                couponEvent,
                "CouponReserved",
                payload(PROMOTION_EXAMPLES, "CouponReserved", orderId),
                orderId);
        send(
                COUPON_TOPIC,
                couponEvent,
                "CouponReserved",
                payload(PROMOTION_EXAMPLES, "CouponReserved", orderId),
                orderId);
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(reserved(orderId, "coupon_reserved")).isTrue());
        send(
                STOCK_TOPIC,
                stockEvent,
                "StockReserved",
                payload(CATALOG_EXAMPLES, "StockReserved", orderId),
                orderId);
        send(
                STOCK_TOPIC,
                stockEvent,
                "StockReserved",
                payload(CATALOG_EXAMPLES, "StockReserved", orderId),
                orderId);

        awaitStatus(orderId, "AWAITING_PAYMENT");
        await().during(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                assertThat(outboxTypes(orderId))
                                        .containsExactly("OrderPlaced", "OrderAwaitingPayment"));
    }

    @Test
    @DisplayName("재고 예약이 성공한 뒤 쿠폰 예약에 실패하면 COUPON_UNAVAILABLE로 취소하고 OrderCancelled만 낸다")
    void couponFailsAfterStock() throws Exception {
        UUID orderId = couponOrder();

        stock("StockReserved", orderId);
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(reserved(orderId, "stock_reserved")).isTrue());
        coupon("CouponReservationFailed", orderId);

        awaitStatus(orderId, "CANCELLED");
        assertThat(cancelReason(orderId)).isEqualTo("COUPON_UNAVAILABLE");
        assertThat(outboxTypes(orderId)).containsExactly("OrderPlaced", "OrderCancelled");
    }

    @Test
    @DisplayName("쿠폰 예약이 성공한 뒤 재고 예약에 실패하면 STOCK_UNAVAILABLE로 취소하고, 늦게 온 성공은 바꾸지 않는다")
    void stockFailsAfterCoupon() throws Exception {
        UUID orderId = couponOrder();

        coupon("CouponReserved", orderId);
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(reserved(orderId, "coupon_reserved")).isTrue());
        stock("StockReservationFailed", orderId);
        awaitStatus(orderId, "CANCELLED");

        UUID other = couponOrder();
        stock("StockReservationFailed", other);
        awaitStatus(other, "CANCELLED");
        UUID late = coupon("CouponReserved", other);
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(processed(late)).isTrue());

        for (UUID id : List.of(orderId, other)) {
            assertThat(status(id)).isEqualTo("CANCELLED");
            assertThat(cancelReason(id)).isEqualTo("STOCK_UNAVAILABLE");
            assertThat(outboxTypes(id)).containsExactly("OrderPlaced", "OrderCancelled");
        }
    }

    @Test
    @DisplayName("두 예약이 모두 실패하면 먼저 받은 실패의 사유로 한 번만 취소한다")
    void bothFail() throws Exception {
        UUID orderId = couponOrder();

        coupon("CouponReservationFailed", orderId);
        awaitStatus(orderId, "CANCELLED");
        UUID late = stock("StockReservationFailed", orderId);
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(processed(late)).isTrue());

        assertThat(cancelReason(orderId)).isEqualTo("COUPON_UNAVAILABLE");
        assertThat(outboxTypes(orderId)).containsExactly("OrderPlaced", "OrderCancelled");
    }

    @Test
    @DisplayName("두 결과를 같은 주문에 동시에 처리해도 상태가 하나로 정해지고 주문 이벤트는 한 번만 나간다")
    void concurrentResults() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 20; i++) {
                UUID ok = couponOrder();
                concurrently(
                        pool,
                        () ->
                                awaitPayment.execute(
                                        new AwaitPaymentCommand(
                                                ok, AwaitPaymentCommand.Reservation.STOCK)),
                        () ->
                                awaitPayment.execute(
                                        new AwaitPaymentCommand(
                                                ok, AwaitPaymentCommand.Reservation.COUPON)));
                assertThat(status(ok)).isEqualTo("AWAITING_PAYMENT");
                assertThat(outboxTypes(ok)).containsExactly("OrderPlaced", "OrderAwaitingPayment");

                UUID failed = couponOrder();
                concurrently(
                        pool,
                        () ->
                                awaitPayment.execute(
                                        new AwaitPaymentCommand(
                                                failed, AwaitPaymentCommand.Reservation.STOCK)),
                        () ->
                                cancel.execute(
                                        new CancelOrderCommand(
                                                failed,
                                                CancelOrderCommand.Reason.COUPON_UNAVAILABLE)));
                assertThat(status(failed)).isEqualTo("CANCELLED");
                assertThat(outboxTypes(failed)).containsExactly("OrderPlaced", "OrderCancelled");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** 두 작업을 장벽에서 같이 출발시키고 둘 다 끝날 때까지 기다린다. */
    private static void concurrently(ExecutorService pool, Runnable first, Runnable second)
            throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> start(barrier, first), pool);
        CompletableFuture<Void> b = CompletableFuture.runAsync(() -> start(barrier, second), pool);
        CompletableFuture.allOf(a, b).get();
    }

    private static void start(CyclicBarrier barrier, Runnable task) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        task.run();
    }

    /** 플랫폼 쿠폰 3,000원을 쓴 PENDING 주문을 저장하고 OrderPlaced를 outbox에 넣는다(주문 생성 API와 같은 결과). */
    private UUID couponOrder() {
        UUID sellerId = UUID.randomUUID();
        Order order =
                Order.place(
                        UUID.randomUUID(),
                        List.of(
                                new OrderItem(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        "반팔 티셔츠",
                                        sellerId,
                                        "길동상회",
                                        UUID.randomUUID(),
                                        List.of(),
                                        List.of(),
                                        19_900,
                                        3)),
                        ShippingAddress.of("홍길동", "010-1234-5678", "06236", "주소", null),
                        Instant.now());
        order.applyCoupons(
                UUID.randomUUID(), List.of(new SellerOrderDiscount(sellerId, null, 0, 3_000)));
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

    /** 재고 예약 결과를 보내고 이벤트 ID를 준다. */
    private UUID stock(String type, UUID orderId) throws Exception {
        UUID eventId = UUID.randomUUID();
        send(STOCK_TOPIC, eventId, type, payload(CATALOG_EXAMPLES, type, orderId), orderId);
        return eventId;
    }

    /** 쿠폰 예약 결과를 보내고 이벤트 ID를 준다. */
    private UUID coupon(String type, UUID orderId) throws Exception {
        UUID eventId = UUID.randomUUID();
        send(COUPON_TOPIC, eventId, type, payload(PROMOTION_EXAMPLES, type, orderId), orderId);
        return eventId;
    }

    private String payload(Path examples, String type, UUID orderId) throws Exception {
        ObjectNode node =
                (ObjectNode) json.readTree(Files.readString(examples.resolve(type + ".json")));
        node.put("orderId", orderId.toString());
        return json.writeValueAsString(node);
    }

    private void awaitStatus(UUID orderId, String status) {
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(status(orderId)).isEqualTo(status));
    }

    private String status(UUID orderId) {
        return jdbc.sql("SELECT status FROM orders WHERE id = :id")
                .param("id", orderId)
                .query(String.class)
                .single();
    }

    private String cancelReason(UUID orderId) {
        return jdbc.sql("SELECT cancel_reason FROM orders WHERE id = :id")
                .param("id", orderId)
                .query(String.class)
                .single();
    }

    private boolean reserved(UUID orderId, String column) {
        return jdbc.sql("SELECT " + column + " FROM orders WHERE id = :id")
                .param("id", orderId)
                .query(Boolean.class)
                .single();
    }

    /** 리스너가 이 이벤트를 처리했는가(처리 기록). */
    private boolean processed(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM processed_event WHERE event_id = :id")
                        .param("id", eventId)
                        .query(Long.class)
                        .single()
                > 0;
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

    private void send(String topic, UUID eventId, String type, String payload, UUID key)
            throws Exception {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, key.toString(), payload);
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
