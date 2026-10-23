package com.openmarket.promotion.command.domain;

/** 쿠폰 발행 주체. 할인을 부담하는 쪽이다(docs/architecture/promotion.md "범위 결정"). */
public enum CouponIssuer {
    /** 관리자가 만든다. 플랫폼이 부담한다. */
    PLATFORM,
    /** 판매자가 만든다. 그 판매자의 모든 상품에 쓰고 판매자가 부담한다. */
    SELLER
}
