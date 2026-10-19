package com.openmarket.catalog.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 입점 승인 이벤트가 아직 catalog에 오지 않아 판매자 사본이 없다. 잠시 뒤 다시 하면 된다. */
public class SellerNotReadyException extends BusinessException {

    public SellerNotReadyException() {
        super(
                ErrorType.CONFLICT,
                "CATALOG_SELLER_NOT_READY",
                "판매자 정보를 준비하고 있습니다. 잠시 후 다시 시도해 주세요.");
    }
}
