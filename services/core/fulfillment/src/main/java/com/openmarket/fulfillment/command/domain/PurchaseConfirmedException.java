package com.openmarket.fulfillment.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 구매확정한 하위 주문에는 반품·교환을 신청할 수 없다. 409다. */
public class PurchaseConfirmedException extends BusinessException {

    public PurchaseConfirmedException() {
        super(
                ErrorType.CONFLICT,
                "FULFILLMENT_PURCHASE_CONFIRMED",
                "구매를 확정한 주문은 반품이나 교환을 신청할 수 없습니다.");
    }
}
