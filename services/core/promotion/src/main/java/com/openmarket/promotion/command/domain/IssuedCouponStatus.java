package com.openmarket.promotion.command.domain;

/** 발급 쿠폰 상태(docs/architecture/promotion.md "발급 쿠폰"). 만료는 상태가 아니라 만료 시각으로 정한다. */
public enum IssuedCouponStatus {
    AVAILABLE,
    /** 결제 전 주문에 묶였다. */
    RESERVED,
    /** 결제된 주문에 쓰였다. 끝 상태다. */
    USED
}
