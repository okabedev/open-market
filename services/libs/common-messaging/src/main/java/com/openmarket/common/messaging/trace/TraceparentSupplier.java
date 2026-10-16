package com.openmarket.common.messaging.trace;

import org.jspecify.annotations.Nullable;

/** 현재 트레이스 컨텍스트를 W3C {@code traceparent} 문자열로 돌려준다. outbox 행에 담아 Kafka 소비자까지 트레이스를 잇는다. */
@FunctionalInterface
public interface TraceparentSupplier {

    /** 트레이싱이 꺼져 있거나 현재 스팬이 없으면 {@code null}이다. */
    @Nullable String current();
}
