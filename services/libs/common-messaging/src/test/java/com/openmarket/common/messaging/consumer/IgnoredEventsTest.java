package com.openmarket.common.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class IgnoredEventsTest {

    private static final Logger log = LoggerFactory.getLogger(IgnoredEventsTest.class);

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void addRegistry() {
        Metrics.addRegistry(registry);
    }

    @AfterEach
    void removeRegistry() {
        Metrics.removeRegistry(registry);
        registry.close();
    }

    @Test
    @DisplayName("리스너가 처리하는 중에 넘긴 이벤트를 리스너별로 센다")
    void countsIgnoredEventsPerListener() {
        IgnoredEvents.handling(
                "order.payment-approved",
                () -> {
                    IgnoredEvents.warn(log, "결제 완료로 바꿀 수 없는 상태라 넘긴다: orderId={}", "o-1");
                    return true;
                });

        assertThat(ignored("order.payment-approved")).isEqualTo(1);
    }

    @Test
    @DisplayName("리스너 밖(스케줄러, API)에서 넘긴 것은 이벤트가 아니라 세지 않는다")
    void doesNotCountOutsideListener() {
        IgnoredEvents.warn(log, "취소할 수 없는 상태라 넘긴다: orderId={}", "o-1");

        assertThat(registry.find(IgnoredEvents.METRIC).counters()).isEmpty();
    }

    @Test
    @DisplayName("처리가 끝나면 리스너 표시를 지운다")
    void clearsListenerAfterHandling() {
        IgnoredEvents.handling("order.payment-approved", () -> true);

        IgnoredEvents.warn(log, "넘긴다");

        assertThat(registry.find(IgnoredEvents.METRIC).counters()).isEmpty();
    }

    private double ignored(String listener) {
        Counter counter = registry.find(IgnoredEvents.METRIC).tag("listener", listener).counter();
        return counter == null ? 0 : counter.count();
    }
}
