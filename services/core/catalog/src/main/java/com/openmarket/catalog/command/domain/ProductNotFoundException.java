package com.openmarket.catalog.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 상품이 없거나 요청한 판매자의 상품이 아니다. */
public class ProductNotFoundException extends BusinessException {

    public ProductNotFoundException() {
        super(ErrorType.NOT_FOUND, "CATALOG_PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다.");
    }
}
