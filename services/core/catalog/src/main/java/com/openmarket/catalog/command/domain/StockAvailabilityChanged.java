package com.openmarket.catalog.command.domain;

import java.util.UUID;

/**
 * 상품 단위 품절·재입고 전환(docs/architecture/context-map.md "catalog.stock"). search가 품절 상품을 뒤로 보낸다.
 *
 * @param inStock 판매 가능한 SKU가 하나라도 있는가
 * @param availabilityRevision 판매 가능 리비전. 상품 스냅샷의 같은 필드와 비교해 큰 쪽을 쓴다
 */
public record StockAvailabilityChanged(UUID productId, boolean inStock, long availabilityRevision) {

    public static final String TYPE = "StockAvailabilityChanged";
}
