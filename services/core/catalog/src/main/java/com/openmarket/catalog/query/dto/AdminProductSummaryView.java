package com.openmarket.catalog.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 관리자 상품 목록의 한 줄(ADM-2).
 *
 * @param thumbnailFileId 대표 이미지(첫 장). 없으면 null
 * @param banReason 제재 사유. 제재한 상품만
 */
public record AdminProductSummaryView(
        UUID productId,
        String name,
        ProductStatus status,
        UUID sellerId,
        String storeName,
        @Nullable UUID thumbnailFileId,
        long minPrice,
        @Nullable String banReason,
        Instant updatedAt) {}
