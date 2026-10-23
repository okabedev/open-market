package com.openmarket.promotion.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CouponTest {

    static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");
    static final Instant START = Instant.parse("2026-10-24T00:00:00Z");
    static final Instant END = Instant.parse("2026-10-31T00:00:00Z");

    static CouponTerms fixed(long value) {
        return new CouponTerms(
                "가을 맞이 3천 원", DiscountType.FIXED, value, null, 10_000, 100, START, END, 7);
    }

    static CouponTerms rate(long value, @Nullable Long maxDiscount) {
        return new CouponTerms(
                "가을 맞이 10%", DiscountType.RATE, value, maxDiscount, 0, 100, START, END, 7);
    }

    @Test
    @DisplayName("플랫폼 쿠폰은 판매자 없이 발급 중 상태로 만든다")
    void platform() {
        Coupon coupon = Coupon.platform(fixed(3_000), NOW);

        assertThat(coupon.issuer()).isEqualTo(CouponIssuer.PLATFORM);
        assertThat(coupon.sellerId()).isNull();
        assertThat(coupon.status()).isEqualTo(CouponStatus.ISSUING);
        assertThat(coupon.terms()).isEqualTo(fixed(3_000));
    }

    @Test
    @DisplayName("판매자 쿠폰은 그 판매자의 것이다")
    void seller() {
        UUID sellerId = UUID.randomUUID();
        Coupon coupon = Coupon.seller(sellerId, rate(10, 5_000L), NOW);

        assertThat(coupon.issuer()).isEqualTo(CouponIssuer.SELLER);
        assertThat(coupon.isOwnedBy(sellerId)).isTrue();
        assertThat(coupon.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("정액은 1원 이상, 정률은 1~100%다")
    void discountValue() {
        assertThatThrownBy(() -> Coupon.platform(fixed(0), NOW))
                .isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(() -> Coupon.platform(rate(0, null), NOW))
                .isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(() -> Coupon.platform(rate(101, null), NOW))
                .isInstanceOf(InvalidCouponException.class);
        assertThat(Coupon.platform(rate(100, null), NOW).terms().discountValue()).isEqualTo(100);
    }

    @Test
    @DisplayName("최대 할인은 정률에만 있고 1원 이상이다")
    void maxDiscount() {
        CouponTerms fixedWithMax =
                new CouponTerms("정액", DiscountType.FIXED, 1_000, 500L, 0, 1, START, END, 1);
        assertThatThrownBy(() -> Coupon.platform(fixedWithMax, NOW))
                .isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(() -> Coupon.platform(rate(10, 0L), NOW))
                .isInstanceOf(InvalidCouponException.class);
    }

    @Test
    @DisplayName("이름은 1~50자, 최소 주문 금액은 0원 이상, 발급 수량은 1~1,000,000, 사용 기한은 1~365일이다")
    void ranges() {
        assertThatThrownBy(() -> Coupon.platform(withName(" "), NOW))
                .isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(() -> Coupon.platform(withName("가".repeat(51)), NOW))
                .isInstanceOf(InvalidCouponException.class);
        assertThat(Coupon.platform(withName("가".repeat(50)), NOW).terms().name()).hasSize(50);
        assertThatThrownBy(
                        () ->
                                Coupon.platform(
                                        new CouponTerms(
                                                "쿠폰",
                                                DiscountType.FIXED,
                                                1,
                                                null,
                                                -1,
                                                1,
                                                START,
                                                END,
                                                1),
                                        NOW))
                .isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(() -> Coupon.platform(withQuantity(0), NOW))
                .isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(() -> Coupon.platform(withQuantity(1_000_001), NOW))
                .isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(() -> Coupon.platform(withValidDays(0), NOW))
                .isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(() -> Coupon.platform(withValidDays(366), NOW))
                .isInstanceOf(InvalidCouponException.class);
    }

    @Test
    @DisplayName("발급 기간은 끝이 시작보다 뒤이고 지금보다 뒤다")
    void issuePeriod() {
        assertThatThrownBy(() -> Coupon.platform(withPeriod(END, START), NOW))
                .isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(() -> Coupon.platform(withPeriod(START, START), NOW))
                .isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(
                        () ->
                                Coupon.platform(
                                        withPeriod(NOW.minusSeconds(7200), NOW.minusSeconds(60)),
                                        NOW))
                .isInstanceOf(InvalidCouponException.class);
        // 시작이 지났어도 끝이 남았으면 바로 발급한다.
        assertThat(Coupon.platform(withPeriod(NOW.minusSeconds(60), END), NOW).isIssuableAt(NOW))
                .isTrue();
    }

    @Test
    @DisplayName("발급 기간 안의 발급 중 쿠폰만 내려받을 수 있다. 끝 시각은 포함하지 않는다")
    void issuable() {
        Coupon coupon = Coupon.platform(fixed(3_000), NOW);

        assertThat(coupon.isIssuableAt(START.minusSeconds(1))).isFalse();
        assertThat(coupon.isIssuableAt(START)).isTrue();
        assertThat(coupon.isIssuableAt(END.minusSeconds(1))).isTrue();
        assertThat(coupon.isIssuableAt(END)).isFalse();

        coupon.stop(START);
        assertThat(coupon.isIssuableAt(START)).isFalse();
    }

    @Test
    @DisplayName("중지하면 끝 상태다. 다시 중지하면 아무것도 바꾸지 않는다")
    void stop() {
        Coupon coupon = Coupon.platform(fixed(3_000), NOW);
        Instant stoppedAt = NOW.plusSeconds(60);

        assertThat(coupon.stop(stoppedAt)).isTrue();
        assertThat(coupon.status()).isEqualTo(CouponStatus.STOPPED);
        assertThat(coupon.stoppedAt()).isEqualTo(stoppedAt);

        assertThat(coupon.stop(stoppedAt.plusSeconds(60))).isFalse();
        assertThat(coupon.stoppedAt()).isEqualTo(stoppedAt);
    }

    private static CouponTerms withName(String name) {
        return new CouponTerms(name, DiscountType.FIXED, 1_000, null, 0, 1, START, END, 1);
    }

    private static CouponTerms withQuantity(int quantity) {
        return new CouponTerms("쿠폰", DiscountType.FIXED, 1_000, null, 0, quantity, START, END, 1);
    }

    private static CouponTerms withValidDays(int validDays) {
        return new CouponTerms("쿠폰", DiscountType.FIXED, 1_000, null, 0, 1, START, END, validDays);
    }

    private static CouponTerms withPeriod(Instant start, Instant end) {
        return new CouponTerms("쿠폰", DiscountType.FIXED, 1_000, null, 0, 1, start, end, 1);
    }
}
