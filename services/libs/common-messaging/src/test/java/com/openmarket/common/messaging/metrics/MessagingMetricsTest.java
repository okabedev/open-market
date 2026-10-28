package com.openmarket.common.messaging.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MessagingMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MessagingMetrics metrics = new MessagingMetrics(registry);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    @DisplayName("outbox 이벤트는 트랜잭션이 커밋된 뒤에 이벤트 이름별로 센다")
    void countsOutboxEventsAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        metrics.outboxAppended("OrderPlaced");
        metrics.outboxAppended("OrderPlaced");

        assertThat(outboxCount("OrderPlaced")).isZero();
        complete(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(outboxCount("OrderPlaced")).isEqualTo(2);
    }

    @Test
    @DisplayName("롤백된 트랜잭션의 outbox 이벤트는 세지 않는다")
    void ignoresRolledBackOutboxEvents() {
        TransactionSynchronizationManager.initSynchronization();

        metrics.outboxAppended("PaymentApproved");
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(outboxCount("PaymentApproved")).isZero();
    }

    @Test
    @DisplayName("DLT로 보낸 메시지를 원래 토픽과 실패한 그룹별로 센다")
    void countsDeadLetters() {
        metrics.deadLettered("order.order", "catalog");
        metrics.deadLettered("order.order", null);

        assertThat(
                        registry.get(MessagingMetrics.DEAD_LETTERS)
                                .tag("topic", "order.order")
                                .tag("group", "catalog")
                                .counter()
                                .count())
                .isEqualTo(1);
        assertThat(
                        registry.get(MessagingMetrics.DEAD_LETTERS)
                                .tag("group", "unknown")
                                .counter()
                                .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("이벤트 발생부터 처리까지 걸린 시간을 리스너별로 잰다. 시계가 어긋나 음수면 0으로 둔다")
    void recordsEventDelay() {
        Instant occurredAt = Instant.parse("2026-10-28T00:00:00Z");

        metrics.handled("catalog.order-placed", occurredAt, occurredAt.plusMillis(250));
        metrics.handled("catalog.order-placed", occurredAt, occurredAt.minusSeconds(1));

        Timer timer =
                registry.get(MessagingMetrics.EVENT_DELAY)
                        .tag("listener", "catalog.order-placed")
                        .timer();
        assertThat(timer.count()).isEqualTo(2);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS))
                .isEqualTo((double) Duration.ofMillis(250).toMillis());
    }

    private double outboxCount(String type) {
        var counter = registry.find(MessagingMetrics.OUTBOX_EVENTS).tag("type", type).counter();
        return counter == null ? 0 : counter.count();
    }

    private static void complete(int status) {
        for (TransactionSynchronization synchronization :
                TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(status);
        }
    }
}
