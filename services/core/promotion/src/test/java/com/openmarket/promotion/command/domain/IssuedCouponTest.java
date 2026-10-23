package com.openmarket.promotion.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IssuedCouponTest {

    static final Instant NOW = CouponTest.NOW;
    static final Instant DURING = CouponTest.START.plusSeconds(60);

    @Test
    @DisplayName("발급 쿠폰은 쓸 수 있는 상태이고 만료 시각은 발급 시각 + 사용 기한 일수다")
    void issue() {
        Coupon coupon = Coupon.platform(CouponTest.fixed(3_000), NOW);
        UUID memberId = UUID.randomUUID();

        IssuedCoupon issued = IssuedCoupon.issue(coupon, memberId, DURING);

        assertThat(issued.couponId()).isEqualTo(coupon.id());
        assertThat(issued.memberId()).isEqualTo(memberId);
        assertThat(issued.status()).isEqualTo(IssuedCouponStatus.AVAILABLE);
        assertThat(issued.issuedAt()).isEqualTo(DURING);
        assertThat(issued.expiresAt()).isEqualTo(DURING.plus(Duration.ofDays(7)));
        assertThat(issued.orderId()).isNull();
    }

    @Test
    @DisplayName("발급 기간 밖이거나 중지한 쿠폰은 409 PROMOTION_COUPON_NOT_ISSUING")
    void notIssuing() {
        Coupon coupon = Coupon.platform(CouponTest.fixed(3_000), NOW);

        assertThatThrownBy(() -> IssuedCoupon.issue(coupon, UUID.randomUUID(), NOW))
                .isInstanceOf(CouponNotIssuingException.class)
                .hasFieldOrPropertyWithValue("code", "PROMOTION_COUPON_NOT_ISSUING");
        assertThatThrownBy(() -> IssuedCoupon.issue(coupon, UUID.randomUUID(), CouponTest.END))
                .isInstanceOf(CouponNotIssuingException.class);

        coupon.stop(DURING);
        assertThatThrownBy(() -> IssuedCoupon.issue(coupon, UUID.randomUUID(), DURING))
                .isInstanceOf(CouponNotIssuingException.class);
    }
}
