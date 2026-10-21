package com.openmarket.payment.command.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 환불이 끝났다(docs/architecture/context-map.md "payment.payment"). fulfillment가 클레임을 완료하고 notification이
 * 구매자에게 알린다.
 *
 * @param amount 환불한 금액(원). 0일 수 있다
 */
public record RefundCompleted(
        UUID orderId,
        UUID refundId,
        UUID claimId,
        UUID sellerOrderId,
        long amount,
        Instant refundedAt) {

    public static final String TYPE = "RefundCompleted";
}
