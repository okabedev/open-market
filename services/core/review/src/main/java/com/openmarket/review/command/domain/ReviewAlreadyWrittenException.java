package com.openmarket.review.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 이 주문 항목에는 이미 리뷰를 썼다. 리뷰는 주문 항목마다 한 번만 쓴다(REV-1). */
public class ReviewAlreadyWrittenException extends BusinessException {

    public ReviewAlreadyWrittenException() {
        super(ErrorType.CONFLICT, "REVIEW_ALREADY_WRITTEN", "이미 리뷰를 쓴 주문 상품입니다.");
    }
}
