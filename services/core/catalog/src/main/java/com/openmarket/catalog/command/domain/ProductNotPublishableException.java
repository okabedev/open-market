package com.openmarket.catalog.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 판매를 시작할 수 없다(이미지 없음, 판매 시작할 수 없는 상태). */
public class ProductNotPublishableException extends BusinessException {

    public ProductNotPublishableException(String message) {
        super(ErrorType.UNPROCESSABLE, "CATALOG_PRODUCT_NOT_PUBLISHABLE", message);
    }
}
