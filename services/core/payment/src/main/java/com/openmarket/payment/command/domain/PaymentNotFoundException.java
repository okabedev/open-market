package com.openmarket.payment.command.domain;

import com.openmarket.common.core.error.NotFoundException;

/** 주문의 결제가 없거나(결제 준비 전 포함) 다른 회원의 것이다. 404다. */
public class PaymentNotFoundException extends NotFoundException {

    public PaymentNotFoundException() {
        super("PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다.");
    }
}
