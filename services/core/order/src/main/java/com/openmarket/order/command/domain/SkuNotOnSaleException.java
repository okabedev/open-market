package com.openmarket.order.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 판매 중이 아닌 상품의 SKU다(판매 중지, 제재, 등록 중). 422다. */
public class SkuNotOnSaleException extends BusinessException {

    public SkuNotOnSaleException() {
        super(ErrorType.UNPROCESSABLE, "ORDER_SKU_NOT_ON_SALE", "판매 중인 상품이 아닙니다.");
    }
}
