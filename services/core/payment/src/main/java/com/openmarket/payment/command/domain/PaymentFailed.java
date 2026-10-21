package com.openmarket.payment.command.domain;

import java.util.UUID;

/**
 * 토스가 승인을 거절했거나 조회로 실패를 확인했다(docs/architecture/context-map.md "payment.payment"). 주문은 결제 기한 안에 다시
 * 결제할 수 있다.
 *
 * @param code 토스 오류 코드, 또는 조회한 토스 결제 상태
 */
public record PaymentFailed(UUID orderId, UUID paymentId, String code) {

    public static final String TYPE = "PaymentFailed";
}
