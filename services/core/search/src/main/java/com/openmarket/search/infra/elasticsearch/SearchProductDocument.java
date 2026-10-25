package com.openmarket.search.infra.elasticsearch;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 상품 인덱스 문서의 원본(_source). 필드는 매핑 파일({@code elasticsearch/products-v2.json})과 같다. ID는 문자열로 둔다.
 *
 * <p>품절 전환만 받은 상품은 상태와 내용 필드가 null이다. v1에서 옮긴 문서는 별점·판매량 필드가 없어 null이다(0으로 읽는다).
 */
record SearchProductDocument(
        String productId,
        long revision,
        @Nullable String status,
        @Nullable String sellerId,
        @Nullable String storeName,
        @Nullable String categoryId,
        @Nullable List<String> categoryPath,
        @Nullable String name,
        @Nullable String description,
        @Nullable String thumbnailFileId,
        @Nullable Long minPrice,
        @Nullable Instant publishedAt,
        boolean inStock,
        long availabilityRevision,
        @Nullable BigDecimal ratingAverage,
        @Nullable Long ratingCount,
        @Nullable Long ratingRevision,
        @Nullable Long salesCount,
        @Nullable Long salesRevision) {}
