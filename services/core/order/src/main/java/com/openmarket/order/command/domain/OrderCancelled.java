package com.openmarket.order.command.domain;

import java.util.UUID;

/**
 * 결제 전에 주문을 취소했다(docs/architecture/context-map.md "order.order"). catalog가 예약을 풀고, payment가 결제를 무효로
 * 한다.
 */
public record OrderCancelled(UUID orderId, CancelReason reason) {

    public static final String TYPE = "OrderCancelled";
}
