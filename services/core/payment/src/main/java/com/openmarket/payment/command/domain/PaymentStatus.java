package com.openmarket.payment.command.domain;

/** 결제 상태(docs/glossary.md "결제"). 부분취소 상태는 환불(M4)을 구현할 때 더한다. */
public enum PaymentStatus {
    /** 결제를 기다린다. */
    READY,
    /** 토스에 승인을 요청했고 결과를 기다린다. */
    IN_PROGRESS,
    /** 승인됐다. */
    APPROVED,
    /** 토스가 승인을 거절했다. 같은 주문으로 다시 승인할 수 있다. */
    FAILED,
    /** 주문이 결제 전에 취소돼 결제를 무효로 했거나, 승인된 결제를 토스에서 전액 취소했다. */
    CANCELLED
}
