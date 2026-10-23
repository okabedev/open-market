package com.openmarket.review.query.dto;

import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 상품 별점 요약(REV-2). 보이는 리뷰만 센다.
 *
 * @param ratingAverage 소수 첫째 자리로 반올림한 평균. 리뷰가 없으면 null
 */
public record RatingSummaryView(
        UUID productId, long ratingCount, @Nullable BigDecimal ratingAverage) {

    /** 리뷰가 없는 상품. */
    public static RatingSummaryView empty(UUID productId) {
        return new RatingSummaryView(productId, 0, null);
    }
}
