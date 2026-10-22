package com.openmarket.payment.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 다시 시도할 환불 금액이 결제의 남은 금액보다 크다. 409다. */
public class RefundAmountExceededException extends BusinessException {

    public RefundAmountExceededException() {
        super(ErrorType.CONFLICT, "PAYMENT_REFUND_AMOUNT_EXCEEDED", "환불 금액이 남은 결제 금액보다 큽니다.");
    }
}
