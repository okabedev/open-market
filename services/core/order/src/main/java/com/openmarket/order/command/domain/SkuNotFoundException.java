package com.openmarket.order.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** catalog에 없는 SKU다. */
public class SkuNotFoundException extends BusinessException {

    public SkuNotFoundException() {
        super(ErrorType.NOT_FOUND, "ORDER_SKU_NOT_FOUND", "상품을 찾을 수 없습니다.");
    }
}
