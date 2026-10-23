package com.openmarket.promotion.command.domain;

import java.util.List;
import java.util.UUID;

/**
 * 주문 쿠폰을 묶지 못했다(docs/architecture/context-map.md "promotion.issued-coupon"). 아무것도 묶지 않았다. order가
 * 주문을 취소한다.
 *
 * @param reason 지금은 {@code COUPON_UNAVAILABLE} 하나다(order의 취소 사유와 같다)
 * @param issuedCouponIds 쓸 수 없는 쿠폰(남의 쿠폰, 이미 묶임·사용됨, 만료, 없는 쿠폰)
 */
public record CouponReservationFailed(UUID orderId, String reason, List<UUID> issuedCouponIds) {

    public static final String TYPE = "CouponReservationFailed";
    public static final String COUPON_UNAVAILABLE = "COUPON_UNAVAILABLE";
}
