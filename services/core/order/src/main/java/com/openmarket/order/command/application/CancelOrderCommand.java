package com.openmarket.order.command.application;

import java.util.UUID;

/** 결제 전 주문 취소. 사유는 도메인 {@code CancelReason}과 같다. */
public record CancelOrderCommand(UUID orderId, Reason reason) {

    public enum Reason {
        STOCK_UNAVAILABLE,
        COUPON_UNAVAILABLE,
        PAYMENT_TIMEOUT,
        BUYER_CANCELLED
    }
}
