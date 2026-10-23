package com.openmarket.review.query.dao;

import static com.openmarket.review.command.domain.QRatingSummary.ratingSummary;

import com.openmarket.review.query.dto.RatingSummaryView;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/** 상품 별점 요약 조회. */
@Repository
public class RatingSummaryQueryDao {

    private final JPAQueryFactory query;

    public RatingSummaryQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    /** 요약 행이 없으면(리뷰가 한 번도 없었던 상품) 빈 값. */
    public Optional<RatingSummaryView> find(UUID productId) {
        Tuple row =
                query.select(ratingSummary.ratingCount, ratingSummary.ratingSum)
                        .from(ratingSummary)
                        .where(ratingSummary.productId.eq(productId))
                        .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        long count = longValue(row.get(ratingSummary.ratingCount));
        long sum = longValue(row.get(ratingSummary.ratingSum));
        return Optional.of(new RatingSummaryView(productId, count, average(sum, count)));
    }

    /**
     * 합 ÷ 수를 소수 첫째 자리로 반올림한다. 커맨드 쪽 {@code RatingSummary.average}와 같은
     * 규칙이다(docs/architecture/review.md "별점 요약", 테스트가 같은지 확인한다).
     */
    static @Nullable BigDecimal average(long sum, long count) {
        if (count == 0) {
            return null;
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }

    private static long longValue(@Nullable Long value) {
        return value == null ? 0 : value;
    }
}
