package com.openmarket.order.command.domain;

/** 하위 주문 클레임의 유형(docs/architecture/order.md "하위 주문 클레임"). 상수는 fulfillment 클레임 유형과 같다. */
public enum ClaimType {
    CANCEL,
    RETURN,
    EXCHANGE
}
