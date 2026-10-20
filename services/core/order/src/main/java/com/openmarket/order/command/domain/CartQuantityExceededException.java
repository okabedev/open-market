package com.openmarket.order.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 다시 담아 한 줄의 수량이 99를 넘는다. 422다. */
public class CartQuantityExceededException extends BusinessException {

    public CartQuantityExceededException() {
        super(ErrorType.UNPROCESSABLE, "ORDER_CART_QUANTITY_EXCEEDED", "한 상품은 99개까지 담을 수 있습니다.");
    }
}
