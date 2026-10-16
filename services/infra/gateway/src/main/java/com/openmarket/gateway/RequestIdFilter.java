package com.openmarket.gateway;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 응답에 {@code X-Request-Id}로 이 요청의 trace id를 붙인다. 사용자가 오류를 알릴 때 이 값으로 로그(trace id 필드)와 트레이스를 바로 찾는다.
 * 별도 ID를 만들지 않아 식별자가 하나로 유지된다.
 *
 * <p>보안 필터보다 먼저 실행해 401·403·429 응답에도 붙는다. 서비스에는 trace id가 {@code traceparent}로 넘어간다.
 */
@Component
class RequestIdFilter implements WebFilter, Ordered {

    static final String HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerRequestObservationContext.findCurrent(exchange.getAttributes())
                .map(RequestIdFilter::span)
                .map(span -> span.context().traceId())
                .filter(traceId -> !traceId.isEmpty())
                .ifPresent(traceId -> exchange.getResponse().getHeaders().set(HEADER, traceId));
        return chain.filter(exchange);
    }

    /** 서버 관측을 시작할 때 트레이싱 핸들러가 만든 스팬. 트레이싱이 꺼져 있으면 없다. */
    private static @Nullable Span span(ServerRequestObservationContext context) {
        TracingObservationHandler.TracingContext tracing =
                context.get(TracingObservationHandler.TracingContext.class);
        return tracing != null ? tracing.getSpan() : null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
