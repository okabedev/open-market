package com.openmarket.common.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.messaging.TestEvents.OrderPlacedMessage;
import com.openmarket.common.messaging.metrics.MessagingMetrics;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

// 테스트는 기본으로 트레이싱을 끄므로 실제 Tracer를 쓰려면 켠다.
@SpringBootTest
@AutoConfigureTracing
@Import(PostgresContainerConfiguration.class)
class OutboxIntegrationTest {

    @Autowired Outbox outbox;

    @Autowired TransactionTemplate transaction;

    @Autowired JdbcClient jdbc;

    @Autowired Tracer tracer;

    @Autowired MeterRegistry registry;

    @Test
    @DisplayName("커밋된 outbox 이벤트만 이벤트 이름별로 센다")
    void countsCommittedEvents() {
        String type = "Committed" + UUID.randomUUID();

        transaction.execute(
                status ->
                        outbox.append(
                                "order.order", "o-1", type, new OrderPlacedMessage("o-1", 1)));
        transaction.executeWithoutResult(
                status -> {
                    outbox.append("order.order", "o-2", type, new OrderPlacedMessage("o-2", 1));
                    status.setRollbackOnly();
                });

        Counter counter = registry.get(MessagingMetrics.OUTBOX_EVENTS).tag("type", type).counter();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("트랜잭션 안에서 쌓은 이벤트는 Debezium이 읽는 형식으로 저장된다")
    void appendsEventInTransaction() {
        Instant before = Instant.now();

        UUID id =
                transaction.execute(
                        status ->
                                outbox.append(
                                        "order.order",
                                        "order-1",
                                        "OrderPlaced",
                                        new OrderPlacedMessage("order-1", 15000)));

        assertThat(id).isNotNull();
        assertThat(id.version()).isEqualTo(7);
        Map<String, Object> row = find(id).orElseThrow();
        assertThat(row)
                .containsEntry("aggregate_type", "order.order")
                .containsEntry("aggregate_id", "order-1")
                .containsEntry("type", "OrderPlaced")
                .containsEntry("schema_version", 1)
                .containsEntry("order_id", "order-1")
                .containsEntry("total_amount", "15000");
        assertThat(((Timestamp) row.get("occurred_at")).toInstant())
                .isAfterOrEqualTo(before.minusSeconds(1));
    }

    @Test
    @DisplayName("페이로드 버전을 지정할 수 있다")
    void appendsWithSchemaVersion() {
        UUID id =
                transaction.execute(
                        status ->
                                outbox.append(
                                        "order.order",
                                        "order-1",
                                        "OrderPlaced",
                                        2,
                                        new OrderPlacedMessage("order-1", 15000)));

        assertThat(find(id).orElseThrow()).containsEntry("schema_version", 2);
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 이벤트도 남지 않는다")
    void rollbackDiscardsEvent() {
        UUID[] id = new UUID[1];

        transaction.executeWithoutResult(
                status -> {
                    id[0] =
                            outbox.append(
                                    "order.order",
                                    "order-1",
                                    "OrderPlaced",
                                    new OrderPlacedMessage("order-1", 15000));
                    status.setRollbackOnly();
                });

        assertThat(find(id[0])).isEmpty();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 쌓으면 거절한다")
    void rejectsOutsideTransaction() {
        assertThatThrownBy(
                        () ->
                                outbox.append(
                                        "order.order",
                                        "order-1",
                                        "OrderPlaced",
                                        new OrderPlacedMessage("order-1", 15000)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("현재 스팬을 W3C traceparent로 담는다")
    void recordsTraceparentOfCurrentSpan() {
        Span span = tracer.nextSpan().name("place-order").start();
        UUID id;
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            id = transaction.execute(status -> append());
        } finally {
            span.end();
        }

        // 마지막 필드(trace-flags)는 샘플링 여부와 W3C Level 2 random 플래그에 따라 01 또는 03이다.
        assertThat((String) find(id).orElseThrow().get("traceparent"))
                .matches(
                        "00-"
                                + span.context().traceId()
                                + "-"
                                + span.context().spanId()
                                + "-0[13]");
    }

    @Test
    @DisplayName("현재 스팬이 없으면 traceparent를 비워 둔다")
    void leavesTraceparentEmptyWithoutSpan() {
        UUID id = transaction.execute(status -> append());

        assertThat(find(id).orElseThrow().get("traceparent")).isNull();
    }

    private UUID append() {
        return outbox.append(
                "order.order", "order-1", "OrderPlaced", new OrderPlacedMessage("order-1", 15000));
    }

    private Optional<Map<String, Object>> find(UUID id) {
        return jdbc
                .sql(
                        """
                        SELECT aggregate_type, aggregate_id, type, schema_version, traceparent, occurred_at,
                               payload->>'orderId' AS order_id, payload->>'totalAmount' AS total_amount
                          FROM outbox_event WHERE id = :id
                        """)
                .param("id", id)
                .query()
                .listOfRows()
                .stream()
                .findFirst();
    }
}
