package com.openmarket.payment.command.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 승인된 결제를 토스에서 전액 취소했다(docs/architecture/context-map.md "payment.payment"). 돈이 빠져나가지 않은 결제를 무효로 할
 * 때는 내지 않는다.
 *
 * @param amount 취소한 금액(원). 전액 취소라 결제 금액이다
 */
public record PaymentCancelled(
        UUID orderId, UUID paymentId, long amount, Reason reason, Instant cancelledAt) {

    public static final String TYPE = "PaymentCancelled";

    /** 취소 이유. */
    public enum Reason {
        /** 결제 전에 취소된 주문의 보상 취소(PAY-3). */
        ORDER_CANCELLED
    }
}
