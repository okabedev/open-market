package com.openmarket.search.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 검색 결과 상품 하나.
 *
 * @param minPrice SKU 가격 중 가장 낮은 값(원)
 * @param inStock 판매 가능한 SKU가 있는가. 품절이면 false
 * @param thumbnailFileId 첫 이미지. {@code /files/{id}/w300}으로 받는다. 없으면 null
 */
public record ProductSearchItemView(
        UUID productId,
        String name,
        String storeName,
        long minPrice,
        boolean inStock,
        @Nullable UUID thumbnailFileId,
        @Nullable Instant publishedAt) {}
