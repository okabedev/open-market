package com.openmarket.common.messaging.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 메시징 지표(docs/architecture/observability.md "지표"). Grafana 대시보드와 알림이 읽는다.
 *
 * <ul>
 *   <li>{@value #OUTBOX_EVENTS}: 커밋된 outbox 이벤트 수(이벤트 이름별). 비즈니스 대시보드의 주문·결제 수다
 *   <li>{@value #DEAD_LETTERS}: DLT로 보낸 메시지 수(원래 토픽, 실패한 그룹별)
 *   <li>{@value #EVENT_DELAY}: 이벤트를 쌓은 시각부터 리스너가 처리를 마칠 때까지(리스너별). Saga 단계마다 걸린 시간이다
 * </ul>
 *
 * <p>무시한 이벤트 수는 유스케이스가 부르는 {@code IgnoredEvents}가 센다.
 */
public class MessagingMetrics {

    public static final String OUTBOX_EVENTS = "messaging.outbox.events";
    public static final String DEAD_LETTERS = "messaging.dead.letters";
    public static final String EVENT_DELAY = "messaging.event.delay";

    private static final String UNKNOWN = "unknown";

    private final MeterRegistry registry;

    public MessagingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 트랜잭션 안이면 커밋된 뒤에 센다. 롤백되면 세지 않는다. */
    public void outboxAppended(String type) {
        Counter counter =
                Counter.builder(OUTBOX_EVENTS)
                        .description("커밋된 outbox 이벤트 수")
                        .tag("type", type)
                        .register(registry);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            counter.increment();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_COMMITTED) {
                            counter.increment();
                        }
                    }
                });
    }

    public void deadLettered(String topic, @Nullable String group) {
        Counter.builder(DEAD_LETTERS)
                .description("DLT로 보낸 메시지 수")
                .tag("topic", topic)
                .tag("group", group == null ? UNKNOWN : group)
                .register(registry)
                .increment();
    }

    public void handled(String listener, Instant occurredAt, Instant now) {
        Duration delay = Duration.between(occurredAt, now);
        Timer.builder(EVENT_DELAY)
                .description("이벤트 발생부터 리스너 처리 완료까지")
                .tag("listener", listener)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(10))
                .maximumExpectedValue(Duration.ofMinutes(10))
                .register(registry)
                .record(delay.isNegative() ? Duration.ZERO : delay);
    }
}
