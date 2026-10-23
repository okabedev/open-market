package com.openmarket.order.command.domain;

/** 결제 전 주문 취소의 이유(docs/glossary.md "취소 사유"). */
public enum CancelReason {
    /** 재고 예약 실패. */
    STOCK_UNAVAILABLE,
    /** 쿠폰 예약 실패. */
    COUPON_UNAVAILABLE,
    /** 결제 대기 시간 초과(주문 만료). */
    PAYMENT_TIMEOUT,
    /** 결제 전 구매자 취소. */
    BUYER_CANCELLED
}
