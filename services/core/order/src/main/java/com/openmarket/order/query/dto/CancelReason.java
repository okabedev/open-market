package com.openmarket.order.query.dto;

/** 조회용 취소 사유. 상수는 커맨드 쪽 {@code CancelReason}과 같다. */
public enum CancelReason {
    STOCK_UNAVAILABLE,
    COUPON_UNAVAILABLE,
    PAYMENT_TIMEOUT,
    BUYER_CANCELLED
}
