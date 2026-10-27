package com.openmarket.common.messaging.deadletter;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * DLT 메시지 한 건(관리자 목록). 페이로드와 예외 메시지는 넣지 않는다. 원인은 trace id로 로그와 트레이스에서 찾는다.
 *
 * @param consumerGroup 실패한 리스너 그룹
 * @param eventId 헤더 {@code id}. 읽을 수 없었으면 비어 있다
 * @param eventType 헤더 {@code type}
 * @param originalOffset 원래 오프셋. 원래 위치 헤더가 없었으면 -1
 * @param exceptionClass 실패 원인 예외 클래스
 * @param status {@code PENDING}, {@code RETRIED}, {@code DISCARDED}
 * @param resolvedBy 다시 보내거나 버린 관리자(토큰의 {@code sub})
 */
public record DeadLetterView(
        UUID id,
        String consumerGroup,
        @Nullable UUID eventId,
        @Nullable String eventType,
        String originalTopic,
        int originalPartition,
        long originalOffset,
        @Nullable String exceptionClass,
        @Nullable String traceId,
        String status,
        Instant deadLetteredAt,
        @Nullable Instant resolvedAt,
        @Nullable String resolvedBy,
        @Nullable String discardReason) {}
