package com.openmarket.order.command.domain;

/** 하위 주문 클레임의 상태(docs/architecture/order.md "하위 주문 클레임"). fulfillment 클레임 이벤트로 바뀐다. */
public enum ClaimStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    MEDIATING,
    COMPLETED
}
