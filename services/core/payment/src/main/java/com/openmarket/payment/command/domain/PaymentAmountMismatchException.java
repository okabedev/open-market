package com.openmarket.payment.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 승인 요청의 금액이 결제 금액과 다르다(PAY-1). 토스를 부르지 않는다. 422다. */
public class PaymentAmountMismatchException extends BusinessException {

    public PaymentAmountMismatchException() {
        super(ErrorType.UNPROCESSABLE, "PAYMENT_AMOUNT_MISMATCH", "결제 금액이 주문 금액과 다릅니다.");
    }
}
