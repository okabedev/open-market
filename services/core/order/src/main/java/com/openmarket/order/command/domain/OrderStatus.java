package com.openmarket.order.command.domain;

/** 주문 상태(docs/glossary.md "주문"). */
public enum OrderStatus {
    /** 재고 예약을 기다린다. */
    PENDING,
    /** 예약이 끝나 결제를 기다린다. */
    AWAITING_PAYMENT,
    /** 결제가 끝났다. */
    PAID,
    /** 결제 전에 취소됐다. 사유는 {@link CancelReason}. */
    CANCELLED
}
