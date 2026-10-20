package com.openmarket.order.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 장바구니 입력 규칙을 어겼다. 400이다. 메시지에 입력값을 넣지 않는다. */
public class InvalidCartInputException extends BusinessException {

    public InvalidCartInputException(String message) {
        super(ErrorType.INVALID, "ORDER_INVALID_INPUT", message);
    }
}
