package com.openmarket.common.messaging.consumer;

import io.micrometer.core.instrument.Metrics;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 상태 전이가 맞지 않아 이벤트를 처리하지 않고 넘길 때 유스케이스가 부른다(docs/architecture/messaging.md "순서"). 경고 로그를 남기고, 리스너가
 * 처리하는 중이면 {@value #METRIC}를 리스너별로 센다.
 *
 * <pre>{@code
 * IgnoredEvents.warn(log, "결제 완료로 바꿀 수 없는 상태라 넘긴다: orderId={}, status={}", id, status);
 * }</pre>
 *
 * <p>같은 유스케이스를 스케줄러나 API가 불러 넘긴 것은 이벤트가 아니라 로그만 남긴다. 리스너 이름은 {@link IdempotentEventProcessor}가 핸들러를
 * 실행하는 동안 이 스레드에 둔다. 유스케이스 생성자를 바꾸지 않도록 Micrometer 전역 레지스트리에 센다(Boot는 레지스트리를 전역 레지스트리에 넣는다).
 */
public final class IgnoredEvents {

    public static final String METRIC = "messaging.events.ignored";

    private static final ThreadLocal<@Nullable String> LISTENER = new ThreadLocal<>();

    private IgnoredEvents() {}

    public static void warn(Logger log, String format, @Nullable Object... arguments) {
        log.warn(format, arguments);
        String listener = LISTENER.get();
        if (listener != null) {
            Metrics.counter(METRIC, "listener", listener).increment();
        }
    }

    static <T> T handling(String listener, Supplier<T> body) {
        String previous = LISTENER.get();
        LISTENER.set(listener);
        try {
            return body.get();
        } finally {
            if (previous == null) {
                LISTENER.remove();
            } else {
                LISTENER.set(previous);
            }
        }
    }
}
