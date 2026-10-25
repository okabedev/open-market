package com.openmarket.promotion.query.dto;

/**
 * 내 쿠폰 목록의 화면 상태(docs/architecture/promotion.md "내 쿠폰"). 발급 쿠폰 상태와 만료 시각을 합친 값이다. 만료는 도메인 상태가 아니다.
 */
public enum IssuedCouponState {
    /** {@code AVAILABLE}이고 만료 시각 전. 주문에 쓸 수 있다 */
    USABLE,
    /** 결제 전 주문에 묶였다. 주문이 취소되면 다시 쓸 수 있다 */
    RESERVED,
    /** 결제된 주문에 쓰였다 */
    USED,
    /** {@code AVAILABLE}이지만 만료 시각이 지났다 */
    EXPIRED
}
