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

    @Test
    @DisplayName("쓸 수 없는 이유: 남의 쿠폰, 묶였거나 쓰인 쿠폰, 만료(만료 시각 포함)")
    void unavailableReason() {
        UUID memberId = UUID.randomUUID();
        IssuedCoupon issued = IssuedCoupon.issue(coupon(), memberId, DURING);

        assertThat(issued.unavailableReason(memberId, DURING)).isNull();
        assertThat(issued.unavailableReason(UUID.randomUUID(), DURING))
                .isEqualTo(CouponUnavailableReason.NOT_OWNED);
        assertThat(issued.unavailableReason(memberId, issued.expiresAt()))
                .isEqualTo(CouponUnavailableReason.EXPIRED);
        issued.reserve(UUID.randomUUID(), DURING);
        assertThat(issued.unavailableReason(memberId, DURING))
                .isEqualTo(CouponUnavailableReason.NOT_AVAILABLE);
    }

    @Test
    @DisplayName("묶기 → 사용. 같은 주문으로 다시 사용하면 아무것도 바꾸지 않는다")
    void reserveAndUse() {
        IssuedCoupon issued = IssuedCoupon.issue(coupon(), UUID.randomUUID(), DURING);
        UUID orderId = UUID.randomUUID();

        issued.reserve(orderId, DURING);
        assertThat(issued.status()).isEqualTo(IssuedCouponStatus.RESERVED);
        assertThat(issued.isBoundTo(orderId)).isTrue();
        assertThat(issued.isBoundTo(UUID.randomUUID())).isFalse();

        assertThat(issued.use(orderId, DURING)).isTrue();
        assertThat(issued.status()).isEqualTo(IssuedCouponStatus.USED);
        assertThat(issued.use(orderId, DURING)).isFalse();
        assertThat(issued.isBoundTo(orderId)).isTrue();
    }

    @Test
    @DisplayName("결제 전 취소로 복원하면 다시 쓸 수 있다. 쓰인 쿠폰은 복원하지 않는다")
    void restore() {
        UUID memberId = UUID.randomUUID();
        IssuedCoupon issued = IssuedCoupon.issue(coupon(), memberId, DURING);
        UUID orderId = UUID.randomUUID();
        issued.reserve(orderId, DURING);

        assertThat(issued.restore(orderId, DURING)).isTrue();
        assertThat(issued.status()).isEqualTo(IssuedCouponStatus.AVAILABLE);
        assertThat(issued.orderId()).isNull();
        assertThat(issued.unavailableReason(memberId, DURING)).isNull();
        assertThat(issued.restore(orderId, DURING)).isFalse();

        issued.reserve(orderId, DURING);
        issued.use(orderId, DURING);
        assertThat(issued.restore(orderId, DURING)).isFalse();
        assertThat(issued.status()).isEqualTo(IssuedCouponStatus.USED);
    }

    @Test
    @DisplayName("쓸 수 있는 쿠폰만 묶는다")
    void reserveRequiresAvailable() {
        IssuedCoupon issued = IssuedCoupon.issue(coupon(), UUID.randomUUID(), DURING);
        issued.reserve(UUID.randomUUID(), DURING);

        assertThatThrownBy(() -> issued.reserve(UUID.randomUUID(), DURING))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Coupon coupon() {
        return Coupon.platform(CouponTest.fixed(3_000), NOW);
    }
}
