package com.openmarket.order.query.dto;

/** 조회용 하위 주문 클레임 유형. 상수는 커맨드 쪽 {@code ClaimType}과 같다. */
public enum ClaimType {
    CANCEL,
    RETURN,
    EXCHANGE
}
