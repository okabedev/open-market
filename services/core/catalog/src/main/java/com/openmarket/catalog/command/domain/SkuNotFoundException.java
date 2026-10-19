package com.openmarket.catalog.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 상품에 없는 SKU다. */
public class SkuNotFoundException extends BusinessException {

    public SkuNotFoundException() {
        super(ErrorType.NOT_FOUND, "CATALOG_SKU_NOT_FOUND", "옵션 조합을 찾을 수 없습니다.");
    }
}
