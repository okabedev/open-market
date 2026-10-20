package com.openmarket.order.command.domain;

import java.util.UUID;

/**
 * 재고 예약이 끝나 결제를 기다린다(docs/architecture/context-map.md "order.order"). payment가 결제 금액을 고정한다.
 *
 * @param amount 결제 금액(원)
 * @param orderName 주문명. 토스 결제창에 보인다
 */
public record OrderAwaitingPayment(UUID orderId, long amount, String orderName) {

    public static final String TYPE = "OrderAwaitingPayment";
}
