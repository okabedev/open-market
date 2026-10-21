package com.openmarket.payment.command.domain;

import java.util.UUID;

/**
 * 토스가 환불을 거절했거나 환불할 수 없다(docs/architecture/context-map.md "payment.payment"). 클레임은 승인 상태로 남고 관리자가
 * 다시 시도한다.
 *
 * @param code 토스 오류 코드, 또는 {@code PAYMENT_NOT_REFUNDABLE}, {@code REFUND_AMOUNT_EXCEEDED}
 */
public record RefundFailed(
        UUID orderId, UUID refundId, UUID claimId, UUID sellerOrderId, String code) {

    public static final String TYPE = "RefundFailed";

    /** 결제가 없거나 환불할 수 없는 상태다. */
    public static final String PAYMENT_NOT_REFUNDABLE = "PAYMENT_NOT_REFUNDABLE";

    /** 환불 금액이 남은 금액보다 크다. */
    public static final String REFUND_AMOUNT_EXCEEDED = "REFUND_AMOUNT_EXCEEDED";

    /** 실패한 환불로 만든다. */
    public static RefundFailed of(Refund refund) {
        String code = refund.failureCode();
        if (code == null) {
            throw new IllegalStateException("실패 코드가 없는 환불이다: " + refund.id());
        }
        return new RefundFailed(
                refund.orderId(), refund.id(), refund.claimId(), refund.sellerOrderId(), code);
    }
}
