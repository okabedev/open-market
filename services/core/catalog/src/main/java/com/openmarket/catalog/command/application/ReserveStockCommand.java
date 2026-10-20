package com.openmarket.catalog.command.application;

import java.util.List;
import java.util.UUID;

/** {@code OrderPlaced}의 주문 항목. 같은 SKU가 여러 줄이어도 된다(수량을 합친다). */
public record ReserveStockCommand(UUID orderId, List<Line> lines) {

    public record Line(UUID skuId, int quantity) {}
}
