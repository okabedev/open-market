package com.openmarket.catalog.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 조정 뒤 실재고가 예약 수량보다 작거나 상한을 넘는다. 422다. */
public class StockOutOfRangeException extends BusinessException {

    public StockOutOfRangeException() {
        super(
                ErrorType.UNPROCESSABLE,
                "CATALOG_STOCK_OUT_OF_RANGE",
                "조정 뒤 재고는 예약된 수량 이상, 999,999 이하여야 합니다.");
    }
}
