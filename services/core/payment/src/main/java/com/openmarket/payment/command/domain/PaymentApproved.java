package com.openmarket.payment.command.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 토스 승인을 확인했다(docs/architecture/context-map.md "payment.payment"). order가 주문을 결제 완료로 바꾼다.
 *
 * @param amount 승인된 금액(원)
 * @param method 토스 결제수단 이름(예: {@code 카드}, {@code 간편결제})
 */
public record PaymentApproved(
        UUID orderId, UUID paymentId, long amount, String method, Instant approvedAt) {

    public static final String TYPE = "PaymentApproved";
}
