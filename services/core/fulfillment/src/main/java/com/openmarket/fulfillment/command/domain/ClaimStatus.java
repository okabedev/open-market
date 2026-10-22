package com.openmarket.fulfillment.command.domain;

/** 클레임 상태(docs/architecture/fulfillment.md "클레임"). */
public enum ClaimStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    MEDIATING,
    COMPLETED;

    /** 진행 중인 클레임인가. 하위 주문마다 하나만 있다(claim_ongoing_idx). */
    public boolean isOngoing() {
        return this == REQUESTED || this == APPROVED || this == MEDIATING;
    }
}
