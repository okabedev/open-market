package com.openmarket.order.query.dto;

/** 장바구니 줄 상태(docs/architecture/order.md "조회"). 판매 가능 수량은 그대로 보여 주지 않고 이 상태로만 알린다. */
public enum CartItemStatus {
    /** 판매 중이고 판매 가능 수량이 담은 수량 이상. */
    AVAILABLE,
    /** 판매 중이고 판매 가능 수량이 있지만 담은 수량보다 적다. */
    INSUFFICIENT_STOCK,
    /** 판매 중이고 판매 가능 수량이 0. */
    SOLD_OUT,
    /** 판매 중지, 제재, 등록 중. */
    NOT_ON_SALE
}
