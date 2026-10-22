package com.openmarket.payment.command.domain;

import com.openmarket.common.core.error.NotFoundException;

/** 환불이 없다. 404다. */
public class RefundNotFoundException extends NotFoundException {

    public RefundNotFoundException() {
        super("PAYMENT_REFUND_NOT_FOUND", "환불을 찾을 수 없습니다.");
    }
}
