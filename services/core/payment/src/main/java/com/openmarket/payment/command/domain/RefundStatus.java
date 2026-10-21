package com.openmarket.payment.command.domain;

/** 환불 상태(docs/architecture/payment.md "환불"). */
public enum RefundStatus {
    /** 환불 작업이 토스 부분취소를 부를 차례다. */
    REQUESTED,
    /** 토스가 부분취소했다. 금액이 0이면 토스를 부르지 않고 바로 이 상태다. */
    COMPLETED,
    /** 토스가 거절했거나 환불할 수 없다. */
    FAILED
}
