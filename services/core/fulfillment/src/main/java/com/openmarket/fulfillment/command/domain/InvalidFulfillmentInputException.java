package com.openmarket.fulfillment.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 판매자·구매자 입력이 규칙에 맞지 않는다. 400이다. */
public class InvalidFulfillmentInputException extends BusinessException {

    public InvalidFulfillmentInputException(String message) {
        super(ErrorType.INVALID, "FULFILLMENT_INVALID_INPUT", message);
    }
}
