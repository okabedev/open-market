package com.openmarket.fulfillment.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 거부 뒤 중재를 요청할 수 있는 기한이 지났다. 409다. */
public class MediationClosedException extends BusinessException {

    public MediationClosedException() {
        super(ErrorType.CONFLICT, "FULFILLMENT_MEDIATION_CLOSED", "중재를 요청할 수 있는 기한이 지났습니다.");
    }
}
