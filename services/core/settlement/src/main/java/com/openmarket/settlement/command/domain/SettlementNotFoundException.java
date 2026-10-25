package com.openmarket.settlement.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 정산이 없다. 판매자에게는 다른 판매자의 정산도 없는 정산이다. */
public class SettlementNotFoundException extends BusinessException {

    public SettlementNotFoundException() {
        super(ErrorType.NOT_FOUND, "SETTLEMENT_NOT_FOUND", "정산을 찾을 수 없습니다.");
    }
}
