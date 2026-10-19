package com.openmarket.catalog.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 상품 입력 규칙 위반(docs/architecture/catalog.md "규칙"). 메시지에 입력값을 넣지 않는다. */
public class InvalidProductException extends BusinessException {

    public InvalidProductException(String message) {
        super(ErrorType.INVALID, "CATALOG_INVALID_INPUT", message);
    }
}
