package com.openmarket.order.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 담을 수 있는 SKU 수(50개)를 넘는다. 422다. */
public class CartFullException extends BusinessException {

    public CartFullException() {
        super(ErrorType.UNPROCESSABLE, "ORDER_CART_FULL", "장바구니에는 50개까지 담을 수 있습니다.");
    }
}
