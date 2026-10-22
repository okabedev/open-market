package com.openmarket.order.query.dto;

/** 조회용 하위 주문 클레임 상태. 상수는 커맨드 쪽 {@code ClaimStatus}와 같다. */
public enum ClaimStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    COMPLETED
}
