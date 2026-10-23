package com.openmarket.promotion.command.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.promotion.command.domain.CouponDiscount.SellerOrderAmount;
import com.openmarket.promotion.command.domain.CouponDiscount.SellerOrderDiscount;
import com.openmarket.promotion.command.domain.CouponDiscount.Unavailable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 할인 계산(docs/architecture/promotion.md "할인 계산"). */
class DiscountCalculatorTest {

    static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");

    final UUID buyerId = UUID.randomUUID();
    final UUID sellerA = UUID.randomUUID();
    final UUID sellerB = UUID.randomUUID();
    final UUID sellerC = UUID.randomUUID();
    final Map<UUID, IssuedCoupon> issued = new HashMap<>();
    final Map<UUID, Coupon> coupons = new HashMap<>();

    @Test
    @DisplayName("쿠폰이 없으면 할인이 0이고 결제 금액은 상품 금액이다")
    void noCoupons() {
        CouponDiscount result =
                calculate(List.of(), order(sellerA, 20_000), order(sellerB, 10_000));

        assertThat(result.applicable()).isTrue();
        assertThat(result.sellerOrders())
                .extracting(SellerOrderDiscount::paymentAmount)
                .containsExactly(20_000L, 10_000L);
        assertThat(result.paymentAmount()).isEqualTo(30_000);
    }

    @Test
    @DisplayName("판매자 쿠폰을 먼저 빼고, 플랫폼 쿠폰은 남은 금액 비율로 하위 주문에 나눈다")
    void sellerThenPlatform() {
        UUID sellerCoupon = issue(sellerCoupon(sellerA, DiscountType.FIXED, 3_000, null, 10_000));
        UUID platformCoupon = issue(platformCoupon(DiscountType.RATE, 10, 5_000L, 0));

        CouponDiscount result =
                calculate(
                        List.of(sellerCoupon, platformCoupon),
                        order(sellerA, 20_000),
                        order(sellerB, 10_000));

        assertThat(result.applicable()).isTrue();
        assertThat(result.platformIssuedCouponId()).isEqualTo(platformCoupon);
        // 플랫폼 대상 = (20,000 - 3,000) + 10,000 = 27,000, 10% = 2,700. 17,000 : 10,000으로 1,700 : 1,000
        SellerOrderDiscount a = result.sellerOrders().get(0);
        assertThat(a.sellerIssuedCouponId()).isEqualTo(sellerCoupon);
        assertThat(a.sellerCouponDiscount()).isEqualTo(3_000);
        assertThat(a.platformCouponDiscount()).isEqualTo(1_700);
        assertThat(a.paymentAmount()).isEqualTo(15_300);
        SellerOrderDiscount b = result.sellerOrders().get(1);
        assertThat(b.sellerIssuedCouponId()).isNull();
        assertThat(b.platformCouponDiscount()).isEqualTo(1_000);
        assertThat(result.paymentAmount()).isEqualTo(30_000 - 3_000 - 2_700);
    }

    @Test
    @DisplayName("나누고 남은 원은 내림으로 버린 값이 큰 하위 주문부터, 같으면 하위 주문 순서로 1원씩 더한다")
    void remainder() {
        UUID platform = issue(platformCoupon(DiscountType.FIXED, 1_000, null, 0));

        CouponDiscount uneven =
                calculate(List.of(platform), order(sellerA, 5_000), order(sellerB, 2_500));
        assertThat(uneven.sellerOrders())
                .extracting(SellerOrderDiscount::platformCouponDiscount)
                .containsExactly(667L, 333L);

        CouponDiscount even =
                calculate(
                        List.of(platform),
                        order(sellerA, 10_000),
                        order(sellerB, 10_000),
                        order(sellerC, 10_000));
        assertThat(even.sellerOrders())
                .extracting(SellerOrderDiscount::platformCouponDiscount)
                .containsExactly(334L, 333L, 333L);
    }

    @Test
    @DisplayName("정률은 내림하고 최대 할인으로 자른다. 할인은 대상 금액을 넘지 않는다")
    void rateAndCap() {
        UUID rate = issue(sellerCoupon(sellerA, DiscountType.RATE, 15, null, 0));
        assertThat(
                        calculate(List.of(rate), order(sellerA, 9_999))
                                .sellerOrders()
                                .getFirst()
                                .sellerCouponDiscount())
                .isEqualTo(1_499);

        UUID capped = issue(sellerCoupon(sellerA, DiscountType.RATE, 15, 1_000L, 0));
        assertThat(
                        calculate(List.of(capped), order(sellerA, 9_999))
                                .sellerOrders()
                                .getFirst()
                                .sellerCouponDiscount())
                .isEqualTo(1_000);

        UUID big = issue(platformCoupon(DiscountType.FIXED, 50_000, null, 0));
        CouponDiscount result =
                calculate(List.of(big), order(sellerA, 20_000), order(sellerB, 10_000));
        assertThat(result.paymentAmount()).isZero();
        assertThat(result.sellerOrders())
                .extracting(SellerOrderDiscount::platformCouponDiscount)
                .containsExactly(20_000L, 10_000L);
    }

    @Test
    @DisplayName("최소 주문 금액은 판매자 쿠폰은 하위 주문 금액, 플랫폼 쿠폰은 판매자 쿠폰을 뺀 주문 금액과 비교한다")
    void minOrderAmount() {
        UUID seller = issue(sellerCoupon(sellerA, DiscountType.FIXED, 5_000, null, 20_001));
        assertThat(calculate(List.of(seller), order(sellerA, 20_000)).unavailable())
                .containsExactly(
                        new Unavailable(seller, CouponUnavailableReason.MIN_ORDER_AMOUNT_NOT_MET));

        UUID okSeller = issue(sellerCoupon(sellerA, DiscountType.FIXED, 5_000, null, 0));
        UUID platform = issue(platformCoupon(DiscountType.FIXED, 1_000, null, 20_000));
        CouponDiscount result = calculate(List.of(okSeller, platform), order(sellerA, 24_999));
        assertThat(result.applicable()).isFalse();
        assertThat(result.unavailable())
                .containsExactly(
                        new Unavailable(
                                platform, CouponUnavailableReason.MIN_ORDER_AMOUNT_NOT_MET));
    }

    @Test
    @DisplayName("쓸 수 없는 쿠폰은 쿠폰마다 이유를 준다")
    void reasons() {
        UUID others =
                issue(platformCoupon(DiscountType.FIXED, 1_000, null, 0), UUID.randomUUID(), NOW);
        UUID unknown = UUID.randomUUID();
        UUID reserved = issue(sellerCoupon(sellerA, DiscountType.FIXED, 1_000, null, 0));
        issued.get(reserved).reserve(UUID.randomUUID(), NOW);
        UUID expired =
                issue(
                        sellerCoupon(sellerB, DiscountType.FIXED, 1_000, null, 0),
                        buyerId,
                        NOW.minus(Duration.ofDays(8)));
        UUID notInOrder = issue(sellerCoupon(sellerC, DiscountType.FIXED, 1_000, null, 0));

        CouponDiscount result =
                calculate(
                        List.of(others, unknown, reserved, expired, notInOrder),
                        order(sellerA, 10_000),
                        order(sellerB, 10_000));

        assertThat(result.applicable()).isFalse();
        assertThat(result.unavailable())
                .containsExactly(
                        new Unavailable(others, CouponUnavailableReason.NOT_OWNED),
                        new Unavailable(unknown, CouponUnavailableReason.NOT_OWNED),
                        new Unavailable(reserved, CouponUnavailableReason.NOT_AVAILABLE),
                        new Unavailable(expired, CouponUnavailableReason.EXPIRED),
                        new Unavailable(notInOrder, CouponUnavailableReason.SELLER_NOT_IN_ORDER));
    }

    @Test
    @DisplayName("플랫폼 쿠폰 둘, 같은 판매자 쿠폰 둘, 같은 쿠폰 두 번은 같은 자리에 두 장이다")
    void duplicateSlots() {
        UUID platform1 = issue(platformCoupon(DiscountType.FIXED, 1_000, null, 0));
        UUID platform2 = issue(platformCoupon(DiscountType.FIXED, 2_000, null, 0));
        UUID seller1 = issue(sellerCoupon(sellerA, DiscountType.FIXED, 1_000, null, 0));
        UUID seller2 = issue(sellerCoupon(sellerA, DiscountType.FIXED, 2_000, null, 0));
        UUID sellerB1 = issue(sellerCoupon(sellerB, DiscountType.FIXED, 1_000, null, 0));

        CouponDiscount result =
                calculate(
                        List.of(platform1, platform2, seller1, seller2, sellerB1, sellerB1),
                        order(sellerA, 10_000),
                        order(sellerB, 10_000));

        assertThat(result.unavailable())
                .extracting(Unavailable::issuedCouponId, Unavailable::reason)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                platform1, CouponUnavailableReason.DUPLICATE_SLOT),
                        org.assertj.core.groups.Tuple.tuple(
                                platform2, CouponUnavailableReason.DUPLICATE_SLOT),
                        org.assertj.core.groups.Tuple.tuple(
                                seller1, CouponUnavailableReason.DUPLICATE_SLOT),
                        org.assertj.core.groups.Tuple.tuple(
                                seller2, CouponUnavailableReason.DUPLICATE_SLOT),
                        org.assertj.core.groups.Tuple.tuple(
                                sellerB1, CouponUnavailableReason.DUPLICATE_SLOT));
    }

    private CouponDiscount calculate(List<UUID> ids, SellerOrderAmount... orders) {
        return DiscountCalculator.calculate(
                buyerId, NOW, ids, issued, coupons, new ArrayList<>(List.of(orders)));
    }

    private static SellerOrderAmount order(UUID sellerId, long amount) {
        return new SellerOrderAmount(sellerId, amount);
    }

    private UUID issue(Coupon coupon) {
        return issue(coupon, buyerId, NOW);
    }

    private UUID issue(Coupon coupon, UUID memberId, Instant at) {
        coupons.put(coupon.id(), coupon);
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(coupon, memberId, at);
        issued.put(issuedCoupon.id(), issuedCoupon);
        return issuedCoupon.id();
    }

    private static Coupon platformCoupon(
            DiscountType type, long value, @Nullable Long maxDiscount, long minOrderAmount) {
        return Coupon.platform(
                terms(type, value, maxDiscount, minOrderAmount), NOW.minus(Duration.ofDays(30)));
    }

    private static Coupon sellerCoupon(
            UUID sellerId,
            DiscountType type,
            long value,
            @Nullable Long maxDiscount,
            long minOrderAmount) {
        return Coupon.seller(
                sellerId,
                terms(type, value, maxDiscount, minOrderAmount),
                NOW.minus(Duration.ofDays(30)));
    }

    private static CouponTerms terms(
            DiscountType type, long value, @Nullable Long maxDiscount, long minOrderAmount) {
        return new CouponTerms(
                "쿠폰",
                type,
                value,
                maxDiscount,
                minOrderAmount,
                1_000,
                NOW.minus(Duration.ofDays(20)),
                NOW.plus(Duration.ofDays(10)),
                7);
    }
}
