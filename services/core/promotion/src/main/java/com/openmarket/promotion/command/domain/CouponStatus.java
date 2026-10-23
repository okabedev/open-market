package com.openmarket.promotion.command.domain;

/** 쿠폰 정책 상태. 발급 가능 여부는 발급 기간으로도 정한다({@link Coupon#isIssuableAt}). */
public enum CouponStatus {
    ISSUING,
    /** 발급 중지. 끝 상태다. 이미 발급한 쿠폰은 쓸 수 있다. */
    STOPPED
}
