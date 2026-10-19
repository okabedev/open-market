package com.openmarket.catalog.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 판매자 상품 목록의 한 줄.
 *
 * @param thumbnailFileId 대표 이미지(첫 장). 없으면 null
 * @param minPrice SKU 가격 중 가장 낮은 값
 * @param available 모든 SKU의 판매 가능 수량 합
 */
public record SellerProductSummaryView(
        UUID productId,
        String name,
        ProductStatus status,
        @Nullable UUID thumbnailFileId,
        long minPrice,
        int skuCount,
        long available,
        Instant updatedAt) {}
