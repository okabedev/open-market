package com.openmarket.payment.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 다른 결제 키로 이미 승인을 요청했거나 승인된 결제다. 409다. */
public class PaymentNotConfirmableException extends BusinessException {

    public PaymentNotConfirmableException() {
        super(ErrorType.CONFLICT, "PAYMENT_NOT_CONFIRMABLE", "이미 처리 중이거나 끝난 결제입니다.");
    }
}
