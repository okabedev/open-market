package com.openmarket.payment.ui.web;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 결제 승인 결과.
 *
 * @param status {@code APPROVED}, 또는 결과를 아직 확인하지 못한 {@code IN_PROGRESS}
 * @param amount 결제 금액(원)
 * @param method 토스 결제수단 이름. 승인됐을 때만 있다
 */
public record ConfirmPaymentResponse(
        UUID paymentId, UUID orderId, Status status, long amount, @Nullable String method) {

    /** 승인 결과의 상태. */
    public enum Status {
        APPROVED,
        IN_PROGRESS
    }
}
