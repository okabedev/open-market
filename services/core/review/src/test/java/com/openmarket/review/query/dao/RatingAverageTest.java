package com.openmarket.review.query.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.review.command.domain.RatingSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 조회 쪽 평균은 커맨드 쪽(이벤트에 싣는 값)과 같다. */
class RatingAverageTest {

    @Test
    @DisplayName("조회 쪽 평균 계산은 별점 요약의 평균 계산과 같다")
    void sameAsCommandSide() {
        for (long count = 0; count <= 200; count++) {
            for (long sum = count; sum <= count * 5; sum++) {
                assertThat(RatingSummaryQueryDao.average(sum, count))
                        .as("sum=%d count=%d", sum, count)
                        .isEqualTo(RatingSummary.average(sum, count));
            }
        }
    }
}
