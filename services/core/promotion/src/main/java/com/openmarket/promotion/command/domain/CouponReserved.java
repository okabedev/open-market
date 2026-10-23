package com.openmarket.promotion.command.domain;

import java.util.List;
import java.util.UUID;

/**
 * 주문 쿠폰을 모두 묶었다(docs/architecture/context-map.md "promotion.issued-coupon"). order가 재고 예약 결과와 합친다.
 */
public record CouponReserved(UUID orderId, List<UUID> issuedCouponIds) {

    public static final String TYPE = "CouponReserved";
}
