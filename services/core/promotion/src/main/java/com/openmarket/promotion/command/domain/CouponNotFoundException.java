package com.openmarket.promotion.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 쿠폰 정책이 없거나 요청한 판매자의 쿠폰이 아니다. */
public class CouponNotFoundException extends BusinessException {

    public CouponNotFoundException() {
        super(ErrorType.NOT_FOUND, "PROMOTION_COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다.");
    }
}
