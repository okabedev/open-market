package com.openmarket.promotion.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.promotion.command.domain.Coupon;
import com.openmarket.promotion.command.domain.CouponReservationFailed;
import com.openmarket.promotion.command.domain.CouponReserved;
import com.openmarket.promotion.command.domain.CouponTerms;
import com.openmarket.promotion.command.domain.DiscountType;
import com.openmarket.promotion.command.domain.InvalidCouponException;
import com.openmarket.promotion.command.domain.IssuedCoupon;
import com.openmarket.promotion.command.domain.IssuedCouponStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 할인 계산과 주문 쿠폰 예약·사용·복원(docs/architecture/promotion.md). */
class OrderCouponUseCasesTest {

    static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");

    final PromotionFakes.Coupons coupons = new PromotionFakes.Coupons();
    final PromotionFakes.IssuedCoupons issued = new PromotionFakes.IssuedCoupons();
    final PromotionFakes.RecordingOutbox outbox = new PromotionFakes.RecordingOutbox();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final ReserveCouponsUseCase reserve = new ReserveCouponsUseCase(issued, outbox, clock);
    final UseCouponsUseCase use = new UseCouponsUseCase(issued, clock);
    final RestoreCouponsUseCase restore = new RestoreCouponsUseCase(issued, clock);
    final CalculateCouponDiscountUseCase calculate =
            new CalculateCouponDiscountUseCase(issued, coupons, clock);

    final UUID buyerId = UUID.randomUUID();
    final UUID orderId = UUID.randomUUID();

    @Test
    @DisplayName("주문 쿠폰을 모두 묶고 CouponReserved를 낸다")
    void reservesAll() {
        UUID first = issue(buyerId, NOW);
        UUID second = issue(buyerId, NOW);

        reserve.execute(new ReserveCouponsCommand(orderId, buyerId, List.of(first, second)));

        assertThat(issued.rows.get(first).status()).isEqualTo(IssuedCouponStatus.RESERVED);
        assertThat(issued.rows.get(second).isBoundTo(orderId)).isTrue();
        assertThat(outbox.events).hasSize(1);
        PromotionFakes.RecordingOutbox.Appended event = outbox.events.getFirst();
        assertThat(event.aggregateType()).isEqualTo("promotion.issued-coupon");
        assertThat(event.aggregateId()).isEqualTo(orderId.toString());
        assertThat(event.payload()).isEqualTo(new CouponReserved(orderId, List.of(first, second)));
    }

    @Test
    @DisplayName("하나라도 쓸 수 없으면(남의 쿠폰, 없는 쿠폰, 만료, 다른 주문에 묶임) 아무것도 묶지 않고 CouponReservationFailed를 낸다")
    void failsWhenAnyUnavailable() {
        UUID mine = issue(buyerId, NOW);
        UUID others = issue(UUID.randomUUID(), NOW);
        UUID unknown = UUID.randomUUID();
        UUID expired = issue(buyerId, NOW.minus(Duration.ofDays(8)));
        UUID bound = issue(buyerId, NOW);
        issued.rows.get(bound).reserve(UUID.randomUUID(), NOW);

        reserve.execute(
                new ReserveCouponsCommand(
                        orderId, buyerId, List.of(mine, others, unknown, expired, bound)));

        assertThat(issued.rows.get(mine).status()).isEqualTo(IssuedCouponStatus.AVAILABLE);
        assertThat(outbox.events.getFirst().payload())
                .isEqualTo(
                        new CouponReservationFailed(
                                orderId,
                                "COUPON_UNAVAILABLE",
                                List.of(others, unknown, expired, bound)));
    }

    @Test
    @DisplayName("쿠폰이 없는 주문과 이미 이 주문에 묶인 쿠폰은 아무것도 하지 않는다")
    void noCouponsOrAlreadyReserved() {
        reserve.execute(new ReserveCouponsCommand(orderId, buyerId, List.of()));
        assertThat(outbox.events).isEmpty();

        UUID coupon = issue(buyerId, NOW);
        reserve.execute(new ReserveCouponsCommand(orderId, buyerId, List.of(coupon)));
        reserve.execute(new ReserveCouponsCommand(orderId, buyerId, List.of(coupon)));
        assertThat(outbox.types()).containsExactly("CouponReserved");
    }

    @Test
    @DisplayName("결제되면 묶인 쿠폰을 쓰고, 결제 전 취소면 되돌린다. 묶인 쿠폰이 없으면 아무것도 하지 않는다")
    void useAndRestore() {
        UUID paid = issue(buyerId, NOW);
        reserve.execute(new ReserveCouponsCommand(orderId, buyerId, List.of(paid)));
        use.execute(new OrderCouponsCommand(orderId));
        use.execute(new OrderCouponsCommand(orderId));
        assertThat(issued.rows.get(paid).status()).isEqualTo(IssuedCouponStatus.USED);

        UUID cancelledOrder = UUID.randomUUID();
        UUID cancelled = issue(buyerId, NOW);
        reserve.execute(new ReserveCouponsCommand(cancelledOrder, buyerId, List.of(cancelled)));
        restore.execute(new OrderCouponsCommand(cancelledOrder));
        assertThat(issued.rows.get(cancelled).status()).isEqualTo(IssuedCouponStatus.AVAILABLE);
        assertThat(issued.rows.get(cancelled).orderId()).isNull();

        restore.execute(new OrderCouponsCommand(UUID.randomUUID()));
        // 되돌린 쿠폰은 다른 주문에 다시 쓴다.
        UUID nextOrder = UUID.randomUUID();
        reserve.execute(new ReserveCouponsCommand(nextOrder, buyerId, List.of(cancelled)));
        assertThat(issued.rows.get(cancelled).isBoundTo(nextOrder)).isTrue();
    }

    @Test
    @DisplayName("할인 계산은 하위 주문마다 할인과 결제 금액을 주고 쿠폰을 묶지 않는다")
    void calculates() {
        UUID platform = issue(buyerId, NOW);
        UUID sellerId = UUID.randomUUID();

        CalculateCouponDiscountUseCase.Result result =
                calculate.execute(
                        new CalculateCouponDiscountCommand(
                                buyerId,
                                List.of(platform),
                                List.of(
                                        new CalculateCouponDiscountCommand.SellerOrder(
                                                sellerId, 20_000))));

        assertThat(result.applicable()).isTrue();
        assertThat(result.platformIssuedCouponId()).isEqualTo(platform);
        assertThat(result.sellerOrders().getFirst().platformCouponDiscount()).isEqualTo(3_000);
        assertThat(result.paymentAmount()).isEqualTo(17_000);
        assertThat(issued.rows.get(platform).status()).isEqualTo(IssuedCouponStatus.AVAILABLE);

        CalculateCouponDiscountUseCase.Result notOwned =
                calculate.execute(
                        new CalculateCouponDiscountCommand(
                                UUID.randomUUID(),
                                List.of(platform),
                                List.of(
                                        new CalculateCouponDiscountCommand.SellerOrder(
                                                sellerId, 20_000))));
        assertThat(notOwned.applicable()).isFalse();
        assertThat(notOwned.unavailable())
                .containsExactly(
                        new CalculateCouponDiscountUseCase.Result.Unavailable(
                                platform, "NOT_OWNED"));
    }

    @Test
    @DisplayName("하위 주문이 없거나 판매자가 겹치면 400 PROMOTION_INVALID_INPUT")
    void invalidOrders() {
        UUID sellerId = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                calculate.execute(
                                        new CalculateCouponDiscountCommand(
                                                buyerId, List.of(), List.of())))
                .isInstanceOf(InvalidCouponException.class);
        assertThatThrownBy(
                        () ->
                                calculate.execute(
                                        new CalculateCouponDiscountCommand(
                                                buyerId,
                                                List.of(),
                                                List.of(
                                                        new CalculateCouponDiscountCommand
                                                                .SellerOrder(sellerId, 1_000),
                                                        new CalculateCouponDiscountCommand
                                                                .SellerOrder(sellerId, 2_000)))))
                .isInstanceOf(InvalidCouponException.class);
    }

    private UUID issue(UUID memberId, Instant at) {
        Coupon coupon =
                coupons.save(
                        Coupon.platform(
                                new CouponTerms(
                                        "3천 원",
                                        DiscountType.FIXED,
                                        3_000,
                                        null,
                                        0,
                                        100,
                                        NOW.minus(Duration.ofDays(20)),
                                        NOW.plus(Duration.ofDays(10)),
                                        7),
                                NOW.minus(Duration.ofDays(30))));
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(coupon, memberId, at);
        issued.rows.put(issuedCoupon.id(), issuedCoupon);
        return issuedCoupon.id();
    }
}
