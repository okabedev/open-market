package com.openmarket.settlement.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 정산 입력 규칙 위반(docs/architecture/settlement.md). 메시지에 입력값을 넣지 않는다. */
public class InvalidSettlementInputException extends BusinessException {

    public InvalidSettlementInputException(String message) {
        super(ErrorType.INVALID, "SETTLEMENT_INVALID_INPUT", message);
    }
}
