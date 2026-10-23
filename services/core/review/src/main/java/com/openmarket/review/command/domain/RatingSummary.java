package com.openmarket.review.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 상품 별점 요약(docs/glossary.md "별점 요약", REV-2). 보이는 리뷰의 수와 별점 합을 둔다. 바꿀 때마다 리비전을 1 올리고, 소비자(search)는
 * 리비전으로 오래된 값을 거른다. 규칙은 docs/architecture/review.md "별점 요약".
 *
 * <p>바꾸는 트랜잭션은 행을 잠그고({@code SELECT ... FOR UPDATE}) 바꾼다. 잠금으로 줄을 세우므로 {@code @Version}을 두지 않는다.
 */
@Entity
@Table(name = "rating_summary")
public class RatingSummary {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "rating_count", nullable = false)
    private long ratingCount;

    @Column(name = "rating_sum", nullable = false)
    private long ratingSum;

    @Column(nullable = false)
    private long revision;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RatingSummary() {}

    private RatingSummary(UUID productId, Instant now) {
        this.productId = productId;
        this.updatedAt = now;
    }

    /** 리뷰가 없는 상품의 요약(리비전 0). */
    public static RatingSummary empty(UUID productId, Instant now) {
        return new RatingSummary(productId, now);
    }

    /** 보이는 리뷰가 늘었다(작성, 다시 보임). */
    public void add(int rating, Instant now) {
        checkRating(rating);
        ratingCount++;
        ratingSum += rating;
        changed(now);
    }

    /** 보이는 리뷰가 줄었다(숨김). */
    public void remove(int rating, Instant now) {
        checkRating(rating);
        if (ratingCount == 0 || ratingSum < rating) {
            throw new IllegalStateException("뺄 리뷰가 없다: productId=" + productId);
        }
        ratingCount--;
        ratingSum -= rating;
        changed(now);
    }

    /** 합 ÷ 수를 소수 첫째 자리로 반올림한 평균. 리뷰가 없으면 null. */
    public @Nullable BigDecimal ratingAverage() {
        return average(ratingSum, ratingCount);
    }

    /** 평균 계산. 조회 쪽도 같은 규칙을 쓴다(docs/architecture/review.md "별점 요약"). */
    public static @Nullable BigDecimal average(long sum, long count) {
        if (count == 0) {
            return null;
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }

    private void changed(Instant now) {
        revision++;
        updatedAt = now;
    }

    private static void checkRating(int rating) {
        if (rating < Review.RATING_MIN || rating > Review.RATING_MAX) {
            throw new IllegalArgumentException("별점은 1~5다: " + rating);
        }
    }

    public UUID productId() {
        return productId;
    }

    public long ratingCount() {
        return ratingCount;
    }

    public long ratingSum() {
        return ratingSum;
    }

    public long revision() {
        return revision;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
