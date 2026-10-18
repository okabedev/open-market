package com.openmarket.common.core.audit;

import org.jspecify.annotations.Nullable;

/**
 * 감사 로그 한 줄. 시각은 기록할 때 정한다.
 *
 * @param actorId 작업한 사람(토큰의 {@code sub})
 * @param action 작업. 대문자 snake case. 예: {@code SELLER_APPLICATION_APPROVED}
 * @param targetType 대상 종류. 예: {@code SELLER_APPLICATION}
 * @param targetId 대상 ID
 * @param detail 작업 사유 같은 부가 설명. 개인정보를 넣지 않는다
 */
public record AuditEntry(
        String actorId,
        String action,
        String targetType,
        String targetId,
        @Nullable String detail) {}
