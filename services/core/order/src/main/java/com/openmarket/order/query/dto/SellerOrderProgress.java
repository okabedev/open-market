package com.openmarket.order.query.dto;

/** 조회용 하위 주문 진행 상태. 상수는 커맨드 쪽 {@code SellerOrderProgress}와 같다. */
public enum SellerOrderProgress {
    PAID,
    ACCEPTED,
    SHIPPED,
    IN_TRANSIT,
    DELIVERED,
    CONFIRMED,
    CANCELLED
}
