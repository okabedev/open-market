package com.openmarket.fulfillment.query.dto;

/** 조회용 클레임 유형. 상수는 커맨드 쪽 {@code ClaimType}과 같다. */
public enum ClaimType {
    CANCEL,
    RETURN,
    EXCHANGE
}
