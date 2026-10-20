package com.openmarket.catalog.command.domain;

import java.util.List;
import java.util.UUID;

/**
 * 재고가 모자라 주문을 예약하지 못했다(docs/architecture/context-map.md "catalog.stock"). order가 주문을 취소한다.
 *
 * @param insufficientSkuIds 모자라거나 catalog에 없는 SKU
 */
public record StockReservationFailed(UUID orderId, List<UUID> insufficientSkuIds) {

    public static final String TYPE = "StockReservationFailed";
}
