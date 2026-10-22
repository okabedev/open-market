package com.openmarket.payment.query.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 토스가 보상 취소를 거절한 결제 하나(docs/architecture/payment.md "관리자 재시도").
 *
 * @param amount 결제 금액(원)
 * @param cancelFailureCode 토스 오류 코드
 * @param cancelAttempt 마지막 시도 번호
 */
public record CancelFailureView(
        UUID paymentId,
        UUID orderId,
        long amount,
        String cancelFailureCode,
        int cancelAttempt,
        Instant cancelRequestedAt) {}
