package com.openmarket.catalog.ui.web;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * 재고 조정.
 *
 * @param delta 늘리면 양수, 줄이면 음수. 0이 아닌 -999,999~999,999
 * @param reason 선택, 100자까지
 */
public record AdjustStockRequest(@NotNull Integer delta, @Nullable String reason) {}
