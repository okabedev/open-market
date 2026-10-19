package com.openmarket.catalog.ui.web;

import java.util.UUID;

/**
 * 조정 결과.
 *
 * @param onHand 조정 뒤 실재고
 * @param reserved 주문으로 예약된 수량
 * @param available 판매 가능 수량
 */
public record AdjustStockResponse(UUID adjustmentId, int onHand, int reserved, int available) {}
