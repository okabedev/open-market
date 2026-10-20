package com.openmarket.catalog.command.domain;

import java.util.UUID;

/** 주문의 SKU를 모두 예약했다(docs/architecture/context-map.md "catalog.stock"). order가 결제 대기로 넘긴다. */
public record StockReserved(UUID orderId) {

    public static final String TYPE = "StockReserved";
}
