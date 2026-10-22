package com.openmarket.payment.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 끝난 환불이거나 다시 시도할 보상 취소가 없다. 409다. */
public class PaymentNotRetryableException extends BusinessException {

    public PaymentNotRetryableException() {
        super(ErrorType.CONFLICT, "PAYMENT_NOT_RETRYABLE", "다시 시도할 환불이나 취소가 없습니다.");
    }
}
