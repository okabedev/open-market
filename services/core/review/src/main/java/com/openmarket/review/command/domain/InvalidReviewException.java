package com.openmarket.review.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 리뷰 입력 규칙 위반(docs/architecture/review.md "리뷰"). 메시지에 입력값을 넣지 않는다. */
public class InvalidReviewException extends BusinessException {

    public InvalidReviewException(String message) {
        super(ErrorType.INVALID, "REVIEW_INVALID_INPUT", message);
    }
}
