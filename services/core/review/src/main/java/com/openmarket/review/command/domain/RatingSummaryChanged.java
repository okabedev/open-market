package com.openmarket.review.command.domain;

import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 상품 별점 요약이 바뀌었다(docs/architecture/context-map.md "review.rating-summary"). 바뀐 뒤의 값을 싣는다. search는
 * revision이 저장된 것보다 클 때만 반영한다.
 *
 * @param ratingAverage 소수 첫째 자리로 반올림한 평균. 보이는 리뷰가 없으면 null
 */
public record RatingSummaryChanged(
        UUID productId,
        long ratingCount,
        long ratingSum,
        @Nullable BigDecimal ratingAverage,
        long revision) {

    public static final String TYPE = "RatingSummaryChanged";

    public static RatingSummaryChanged of(RatingSummary summary) {
        return new RatingSummaryChanged(
                summary.productId(),
                summary.ratingCount(),
                summary.ratingSum(),
                summary.ratingAverage(),
                summary.revision());
    }
}
