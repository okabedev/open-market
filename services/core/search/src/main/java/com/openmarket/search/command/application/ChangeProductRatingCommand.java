package com.openmarket.search.command.application;

import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 상품 별점 요약({@code RatingSummaryChanged}).
 *
 * @param ratingAverage 소수 첫째 자리 평균. 보이는 리뷰가 없으면 null
 * @param revision 별점 요약 리비전
 */
public record ChangeProductRatingCommand(
        UUID productId, long ratingCount, @Nullable BigDecimal ratingAverage, long revision) {}
