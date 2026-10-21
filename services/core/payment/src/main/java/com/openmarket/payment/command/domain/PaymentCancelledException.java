package com.openmarket.payment.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 주문이 결제 전에 취소돼 결제할 수 없다. 이미 승인된 금액은 자동으로 취소된다. 409다. */
public class PaymentCancelledException extends BusinessException {

    public PaymentCancelledException() {
        super(ErrorType.CONFLICT, "PAYMENT_CANCELLED", "주문이 취소되어 결제할 수 없습니다. 승인된 금액은 자동으로 취소됩니다.");
    }
}
