package com.openmarket.payment.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 환불을 다시 시도할 결제가 없거나 환불할 수 없는 상태다. 409다. */
public class PaymentNotRefundableException extends BusinessException {

    public PaymentNotRefundableException() {
        super(ErrorType.CONFLICT, "PAYMENT_NOT_REFUNDABLE", "환불할 수 있는 결제가 없습니다.");
    }
}
