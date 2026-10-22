package com.openmarket.fulfillment.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 클레임 하나(docs/architecture/fulfillment.md "클레임").
 *
 * @param refundAmount 환불 금액(원). 교환은 0
 * @param refundFailureCode 마지막 환불 실패 코드. 환불이 실패한 채 승인 상태일 때만
 * @param approvedAt 승인 시각. 승인됐을 때만
 * @param completedAt 완료 시각. 끝났을 때만
 */
public record ClaimView(
        UUID claimId,
        ClaimType type,
        ClaimStatus status,
        ClaimReason reason,
        Fault fault,
        long refundAmount,
        @Nullable String refundFailureCode,
        Instant requestedAt,
        @Nullable Instant approvedAt,
        @Nullable Instant completedAt) {}
