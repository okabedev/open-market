package com.openmarket.fulfillment.command.domain;

/** 클레임 사유(docs/architecture/fulfillment.md "사유와 귀책"). 사유가 귀책을 정한다. */
public enum ClaimReason {
    CHANGE_OF_MIND(Fault.BUYER),
    DEFECTIVE(Fault.SELLER),
    WRONG_ITEM(Fault.SELLER),
    NOT_AS_DESCRIBED(Fault.SELLER),
    /** 발송 전 취소({@link ClaimType#CANCEL}) 전용. */
    BUYER_CANCEL(Fault.BUYER);

    private final Fault fault;

    ClaimReason(Fault fault) {
        this.fault = fault;
    }

    public Fault fault() {
        return fault;
    }
}
