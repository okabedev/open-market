package com.openmarket.catalog.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 지금 상태에서 할 수 없는 상품 작업. 422다. */
public class ProductStateException extends BusinessException {

    private ProductStateException(String code, String message) {
        super(ErrorType.UNPROCESSABLE, code, message);
    }

    /** 등록 중인 상품을 중지·재개하는 등 상태가 맞지 않는다. */
    static ProductStateException invalidStatus(String message) {
        return new ProductStateException("CATALOG_INVALID_STATUS", message);
    }

    /** 관리자가 제재한 상품이다(CAT-5). */
    public static ProductStateException banned() {
        return new ProductStateException(
                "CATALOG_PRODUCT_BANNED", "관리자가 판매를 제한한 상품입니다. 고객센터로 문의해 주세요.");
    }

    /** 판매자가 정지돼 판매할 수 없다(SEL-3). */
    public static ProductStateException sellerSuspended() {
        return new ProductStateException("CATALOG_SELLER_SUSPENDED", "판매자 정지 중에는 상품을 판매할 수 없습니다.");
    }
}
