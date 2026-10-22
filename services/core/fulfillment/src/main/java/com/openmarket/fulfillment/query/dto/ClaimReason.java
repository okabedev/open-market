package com.openmarket.fulfillment.query.dto;

/** 조회용 클레임 사유. 상수는 커맨드 쪽 {@code ClaimReason}과 같다. */
public enum ClaimReason {
    CHANGE_OF_MIND,
    DEFECTIVE,
    WRONG_ITEM,
    NOT_AS_DESCRIBED,
    BUYER_CANCEL
}
