package com.openmarket.review.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RatingSummaryTest {

    static final Instant NOW = Instant.parse("2026-10-29T01:00:00Z");

    @Test
    @DisplayName("새 요약은 리뷰가 없고 평균이 없다")
    void startsEmpty() {
        RatingSummary summary = RatingSummary.empty(UUID.randomUUID(), NOW);

        assertThat(summary.ratingCount()).isZero();
        assertThat(summary.ratingSum()).isZero();
        assertThat(summary.revision()).isZero();
        assertThat(summary.ratingAverage()).isNull();
    }

    @Test
    @DisplayName("리뷰를 더하면 수와 합이 늘고 리비전이 1 오른다")
    void adds() {
        RatingSummary summary = RatingSummary.empty(UUID.randomUUID(), NOW);

        summary.add(5, NOW);
        summary.add(4, NOW.plusSeconds(1));

        assertThat(summary.ratingCount()).isEqualTo(2);
        assertThat(summary.ratingSum()).isEqualTo(9);
        assertThat(summary.revision()).isEqualTo(2);
        assertThat(summary.updatedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(summary.ratingAverage()).isEqualByComparingTo("4.5");
    }

    @Test
    @DisplayName("평균은 소수 첫째 자리로 반올림한다")
    void roundsAverage() {
        RatingSummary summary = RatingSummary.empty(UUID.randomUUID(), NOW);
        summary.add(5, NOW);
        summary.add(4, NOW);
        summary.add(4, NOW);
        // 13 / 3 = 4.333…
        assertThat(summary.ratingAverage()).isEqualTo(new BigDecimal("4.3"));

        summary.add(5, NOW);
        summary.add(5, NOW);
        summary.add(4, NOW);
        // 27 / 6 = 4.5
        assertThat(summary.ratingAverage()).isEqualTo(new BigDecimal("4.5"));

        RatingSummary halfUp = RatingSummary.empty(UUID.randomUUID(), NOW);
        halfUp.add(5, NOW);
        for (int i = 0; i < 19; i++) {
            halfUp.add(4, NOW);
        }
        // 81 / 20 = 4.05 → 4.1
        assertThat(halfUp.ratingAverage()).isEqualTo(new BigDecimal("4.1"));

        RatingSummary whole = RatingSummary.empty(UUID.randomUUID(), NOW);
        whole.add(4, NOW);
        assertThat(whole.ratingAverage()).isEqualTo(new BigDecimal("4.0"));
    }

    @Test
    @DisplayName("리뷰를 빼면 수와 합이 줄고 리비전이 1 오른다. 마지막 리뷰를 빼면 평균이 없다")
    void removes() {
        RatingSummary summary = RatingSummary.empty(UUID.randomUUID(), NOW);
        summary.add(5, NOW);
        summary.add(2, NOW);

        summary.remove(2, NOW);
        assertThat(summary.ratingCount()).isEqualTo(1);
        assertThat(summary.ratingSum()).isEqualTo(5);
        assertThat(summary.revision()).isEqualTo(3);

        summary.remove(5, NOW);
        assertThat(summary.ratingCount()).isZero();
        assertThat(summary.ratingAverage()).isNull();
        assertThatThrownBy(() -> summary.remove(5, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("별점 1~5만 더하고 뺀다")
    void checksRating() {
        RatingSummary summary = RatingSummary.empty(UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> summary.add(0, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> summary.add(6, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(summary.revision()).isZero();
    }
}
