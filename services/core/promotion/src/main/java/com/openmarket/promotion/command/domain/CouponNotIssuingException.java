package com.openmarket.promotion.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 발급을 중지했거나 발급 기간 밖이라 내려받을 수 없다. */
public class CouponNotIssuingException extends BusinessException {

    public CouponNotIssuingException() {
        super(ErrorType.CONFLICT, "PROMOTION_COUPON_NOT_ISSUING", "지금은 내려받을 수 없는 쿠폰입니다.");
    }
}
