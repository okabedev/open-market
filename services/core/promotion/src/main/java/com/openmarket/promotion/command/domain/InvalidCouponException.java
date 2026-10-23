package com.openmarket.promotion.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 쿠폰 정책 입력 규칙 위반(docs/architecture/promotion.md "쿠폰 정책"). 메시지에 입력값을 넣지 않는다. */
public class InvalidCouponException extends BusinessException {

    public InvalidCouponException(String message) {
        super(ErrorType.INVALID, "PROMOTION_INVALID_INPUT", message);
    }
}
