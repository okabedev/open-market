package com.openmarket.settlement.query.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 카테고리 하나의 수수료율.
 *
 * @param rateBp 0~10,000(bp, 0.01%)
 */
public record CommissionRateView(UUID categoryId, int rateBp, Instant updatedAt) {}
