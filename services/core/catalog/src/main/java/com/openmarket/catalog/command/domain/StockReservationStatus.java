package com.openmarket.catalog.command.domain;

/** 재고 예약 상태(docs/glossary.md "재고 예약"). */
public enum StockReservationStatus {
    /** 주문의 SKU를 모두 예약했다. */
    RESERVED,
    /** 결제가 끝나 실재고에서 뺐다. */
    COMMITTED,
    /** 주문이 취소돼 예약을 풀었다. 예약 없이 취소된 주문도 빈 예약으로 이 상태가 된다. */
    RELEASED,
    /** 재고가 모자라 예약하지 못했다. */
    FAILED
}
