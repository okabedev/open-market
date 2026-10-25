package com.openmarket.review.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 리뷰가 없다. 판매자에게는 다른 판매자의 상품 리뷰도 없는 리뷰다. */
public class ReviewNotFoundException extends BusinessException {

    public ReviewNotFoundException() {
        super(ErrorType.NOT_FOUND, "REVIEW_NOT_FOUND", "리뷰를 찾을 수 없습니다.");
    }
}
