package com.openmarket.catalog.command.domain;

/** 판매 중지 사유. 누가 멈췄는지에 따라 다시 여는 방법이 다르다. */
public enum StopReason {
    /** 판매자가 직접 멈췄다. 판매자가 다시 연다. */
    SELLER,
    /** 판매자가 정지돼 멈췄다. 정지가 풀리면 catalog가 다시 연다. 판매자가 직접 열 수 없다. */
    SELLER_SUSPENDED
}
