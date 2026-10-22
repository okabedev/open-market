package com.openmarket.payment.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 실패한 환불 하나(docs/architecture/payment.md "관리자 재시도").
 *
 * @param amount 환불 금액(원)
 * @param failureCode 토스 오류 코드, 또는 {@code PAYMENT_NOT_REFUNDABLE}, {@code REFUND_AMOUNT_EXCEEDED}
 * @param attempt 마지막 시도 번호
 * @param failedAt 실패를 남긴 시각
 */
public record FailedRefundView(
        UUID refundId,
        UUID orderId,
        UUID claimId,
        UUID sellerOrderId,
        long amount,
        @Nullable String failureCode,
        int attempt,
        Instant requestedAt,
        Instant failedAt) {}
