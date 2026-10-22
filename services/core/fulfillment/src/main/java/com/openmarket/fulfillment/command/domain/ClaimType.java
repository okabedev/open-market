package com.openmarket.fulfillment.command.domain;

/** 클레임 유형(docs/glossary.md "클레임"). */
public enum ClaimType {
    /** 발송 전 취소(CLM-1). 신청과 동시에 승인한다. */
    CANCEL,
    /** 반품(CLM-2). */
    RETURN,
    /** 같은 SKU 교환(CLM-2). 판매자 귀책 사유만 된다. */
    EXCHANGE
}
