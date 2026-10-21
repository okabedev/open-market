package com.openmarket.order.command.domain;

/**
 * 하위 주문 진행 상태(docs/architecture/order.md "하위 주문 진행 상태"). 결제된 하위 주문의 배송 진행이다. 상수 순서가 진행 순서이고 앞으로만
 * 간다.
 */
public enum SellerOrderProgress {
    PAID,
    ACCEPTED,
    SHIPPED,
    IN_TRANSIT,
    DELIVERED,
    CONFIRMED
}
