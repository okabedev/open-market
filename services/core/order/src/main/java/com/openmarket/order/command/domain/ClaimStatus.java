package com.openmarket.order.command.domain;

/**
 * 하위 주문 클레임의 상태(docs/architecture/order.md "하위 주문 클레임"). fulfillment 클레임 이벤트로 바뀐다. 중재는 중재 PR에서 더한다.
 */
public enum ClaimStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    COMPLETED
}
