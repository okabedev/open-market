package com.openmarket.catalog.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 재고 조정 이력 한 줄.
 *
 * @param delta 늘리면 양수, 줄이면 음수
 * @param onHandAfter 조정 뒤 실재고
 * @param reason 판매자가 쓴 사유. 없으면 null
 */
public record StockAdjustmentView(
        UUID adjustmentId,
        int delta,
        int onHandAfter,
        @Nullable String reason,
        Instant adjustedAt) {}
