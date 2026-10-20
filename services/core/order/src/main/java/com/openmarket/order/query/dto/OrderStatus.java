package com.openmarket.order.query.dto;

/** 조회용 주문 상태. 상수는 커맨드 쪽 {@code OrderStatus}와 같다. */
public enum OrderStatus {
    PENDING,
    AWAITING_PAYMENT,
    PAID,
    CANCELLED
}
