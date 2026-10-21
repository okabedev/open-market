package com.openmarket.fulfillment.command.domain;

/** 구매확정 방식(docs/architecture/fulfillment.md "구매확정"). */
public enum ConfirmMode {
    /** 구매자가 직접 확정했다. */
    BUYER,
    /** 확정 기한이 지나 자동으로 확정했다. */
    AUTO
}
