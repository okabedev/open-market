package com.openmarket.common.messaging.trace;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Micrometer Tracing의 {@link Propagator}로 현재 스팬을 헤더 형식으로 꺼낸다. 형식은 설정된 전파 방식(OTel 브리지는 W3C)을 따른다.
 *
 * <p>트레이싱 빈은 서비스 설정에 따라 없을 수 있어 호출할 때 찾는다.
 */
public class MicrometerTraceparentSupplier implements TraceparentSupplier {

    static final String HEADER = "traceparent";

    private final ObjectProvider<Tracer> tracer;
    private final ObjectProvider<Propagator> propagator;

    public MicrometerTraceparentSupplier(
            ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public @Nullable String current() {
        Tracer currentTracer = tracer.getIfAvailable();
        Propagator currentPropagator = propagator.getIfAvailable();
        if (currentTracer == null || currentPropagator == null) {
            return null;
        }
        Span span = currentTracer.currentSpan();
        if (span == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        currentPropagator.inject(
                span.context(),
                carrier,
                (map, key, value) -> {
                    if (map != null) {
                        map.put(key, value);
                    }
                });
        return carrier.get(HEADER);
    }
}
