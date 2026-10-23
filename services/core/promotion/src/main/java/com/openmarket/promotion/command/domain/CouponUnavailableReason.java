package com.openmarket.promotion.command.domain;

/** 주문에 쓸 수 없는 쿠폰의 이유(docs/architecture/promotion.md "할인 계산"). */
public enum CouponUnavailableReason {
    /** 내 쿠폰이 아니다(없는 발급 쿠폰 포함). */
    NOT_OWNED,
    /** 이미 다른 주문에 묶였거나 쓰였다. */
    NOT_AVAILABLE,
    /** 사용 기한이 지났다. */
    EXPIRED,
    /** 적용 대상 금액이 최소 주문 금액보다 작다. */
    MIN_ORDER_AMOUNT_NOT_MET,
    /** 판매자 쿠폰인데 주문에 그 판매자 상품이 없다. */
    SELLER_NOT_IN_ORDER,
    /** 같은 자리(플랫폼, 같은 판매자)에 두 장 이상이다. */
    DUPLICATE_SLOT
}
