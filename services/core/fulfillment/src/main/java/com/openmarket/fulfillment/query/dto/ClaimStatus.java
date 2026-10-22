package com.openmarket.fulfillment.query.dto;

/** 조회용 클레임 상태. 상수는 커맨드 쪽 {@code ClaimStatus}와 같다. */
public enum ClaimStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    MEDIATING,
    COMPLETED
}
