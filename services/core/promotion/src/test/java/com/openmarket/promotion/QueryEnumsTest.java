package com.openmarket.promotion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 조회용 enum은 도메인 enum과 상수가 같다(conventions.md "경계를 넘는 타입"). */
class QueryEnumsTest {

    @Test
    @DisplayName("쿠폰 발행 주체·할인 방식·상태의 조회용 상수는 도메인과 같다")
    void sameConstants() {
        assertThat(names(com.openmarket.promotion.query.dto.CouponIssuer.values()))
                .containsExactly(
                        names(com.openmarket.promotion.command.domain.CouponIssuer.values()));
        assertThat(names(com.openmarket.promotion.query.dto.DiscountType.values()))
                .containsExactly(
                        names(com.openmarket.promotion.command.domain.DiscountType.values()));
        assertThat(names(com.openmarket.promotion.query.dto.CouponStatus.values()))
                .containsExactly(
                        names(com.openmarket.promotion.command.domain.CouponStatus.values()));
        assertThat(
                        names(
                                com.openmarket.promotion.command.application.CreateCouponCommand
                                        .DiscountType.values()))
                .containsExactly(
                        names(com.openmarket.promotion.command.domain.DiscountType.values()));
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }
}
