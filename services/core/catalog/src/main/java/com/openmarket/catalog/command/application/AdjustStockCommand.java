package com.openmarket.catalog.command.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 판매자가 SKU 재고를 조정한다(CAT-3).
 *
 * @param delta 늘리면 양수, 줄이면 음수
 * @param reason 선택, 100자까지
 */
public record AdjustStockCommand(
        UUID sellerId, UUID productId, UUID skuId, int delta, @Nullable String reason) {}
