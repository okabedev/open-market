package com.openmarket.order.command.application;

import java.util.UUID;

/**
 * 예약이 끝난 주문({@code StockReserved}, {@code CouponReserved}).
 *
 * @param reservation 끝난 예약
 */
public record AwaitPaymentCommand(UUID orderId, Reservation reservation) {

    /** 예약 종류. 도메인 {@code Order.Reservation}과 같다. */
    public enum Reservation {
        STOCK,
        COUPON
    }
}
