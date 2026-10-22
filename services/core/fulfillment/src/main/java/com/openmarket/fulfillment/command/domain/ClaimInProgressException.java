package com.openmarket.fulfillment.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 하위 주문에 진행 중인 클레임이 있어 할 수 없다(새 클레임, 구매확정). 409다. */
public class ClaimInProgressException extends BusinessException {

    public ClaimInProgressException() {
        super(ErrorType.CONFLICT, "FULFILLMENT_CLAIM_IN_PROGRESS", "진행 중인 클레임이 있습니다.");
    }
}
