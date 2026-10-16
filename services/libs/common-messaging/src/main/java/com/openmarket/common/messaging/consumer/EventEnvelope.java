package com.openmarket.common.messaging.consumer;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 소비한 이벤트. Kafka 헤더의 공통 필드와 역직렬화한 페이로드를 묶는다. 헤더 형식은 docs/architecture/messaging.md에 있다.
 *
 * @param id 이벤트 ID. 멱등 소비 키다
 * @param type 이벤트 이름. 예: {@code OrderPlaced}
 * @param aggregateId Kafka 키(집계 ID)
 * @param occurredAt 발행자가 이벤트를 쌓은 시각
 * @param schemaVersion 페이로드 버전
 * @param traceparent 발행 시점의 W3C 트레이스 컨텍스트. 트레이싱이 꺼져 있었으면 없다
 * @param payload 이벤트 본문
 */
public record EventEnvelope<T>(
        UUID id,
        String type,
        String aggregateId,
        Instant occurredAt,
        int schemaVersion,
        @Nullable String traceparent,
        T payload) {}
