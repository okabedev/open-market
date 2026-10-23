package com.openmarket.promotion.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 발급 수량을 다 썼다. */
public class CouponSoldOutException extends BusinessException {

    public CouponSoldOutException() {
        super(ErrorType.CONFLICT, "PROMOTION_COUPON_SOLD_OUT", "준비한 쿠폰이 모두 소진됐습니다.");
    }
}
