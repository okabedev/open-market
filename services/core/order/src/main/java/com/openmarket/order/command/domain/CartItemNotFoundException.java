package com.openmarket.order.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 장바구니에 담지 않은 SKU다. */
public class CartItemNotFoundException extends BusinessException {

    public CartItemNotFoundException() {
        super(ErrorType.NOT_FOUND, "ORDER_CART_ITEM_NOT_FOUND", "장바구니에 없는 상품입니다.");
    }
}
