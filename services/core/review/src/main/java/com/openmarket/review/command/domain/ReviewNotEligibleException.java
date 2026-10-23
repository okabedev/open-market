package com.openmarket.review.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 리뷰를 쓸 수 있는 주문 항목이 아니다. 구매확정 전이거나, 남의 주문 항목이거나, 없다. */
public class ReviewNotEligibleException extends BusinessException {

    public ReviewNotEligibleException() {
        super(ErrorType.NOT_FOUND, "REVIEW_NOT_ELIGIBLE", "리뷰를 쓸 수 있는 주문 상품이 아닙니다.");
    }
}
