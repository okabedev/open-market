package com.openmarket.promotion.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.promotion.command.domain.Coupon;
import com.openmarket.promotion.command.domain.CouponIssuer;
import com.openmarket.promotion.command.domain.CouponNotFoundException;
import com.openmarket.promotion.command.domain.CouponStatus;
import com.openmarket.promotion.command.domain.DiscountType;
import com.openmarket.promotion.command.domain.InvalidCouponException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CouponUseCasesTest {

    static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");

    final PromotionFakes.Coupons coupons = new PromotionFakes.Coupons();
    final PromotionFakes.RecordingAuditLog auditLog = new PromotionFakes.RecordingAuditLog();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final CreateCouponUseCase create = new CreateCouponUseCase(coupons, auditLog, clock);
    final StopCouponUseCase stop = new StopCouponUseCase(coupons, auditLog, clock);

    final UUID adminId = UUID.randomUUID();
    final UUID sellerId = UUID.randomUUID();

    @Test
    @DisplayName("관리자가 만들면 플랫폼 쿠폰이고 감사 로그 COUPON_CREATED를 남긴다")
    void adminCreatesPlatformCoupon() {
        UUID couponId = create.execute(command(adminId, null));

        Coupon coupon = coupons.rows.get(couponId);
        assertThat(coupon.issuer()).isEqualTo(CouponIssuer.PLATFORM);
        assertThat(coupon.terms().discountType()).isEqualTo(DiscountType.RATE);
        assertThat(coupon.terms().maxDiscount()).isEqualTo(5_000L);
        assertThat(coupon.createdAt()).isEqualTo(NOW);
        assertThat(auditLog.entries)
                .containsExactly(
                        new AuditEntry(
                                adminId.toString(),
                                "COUPON_CREATED",
                                "COUPON",
                                couponId.toString(),
                                null));
    }

    @Test
    @DisplayName("판매자가 만들면 그 판매자의 쿠폰이고 감사 로그를 남기지 않는다")
    void sellerCreatesSellerCoupon() {
        UUID couponId = create.execute(command(sellerId, sellerId));

        Coupon coupon = coupons.rows.get(couponId);
        assertThat(coupon.issuer()).isEqualTo(CouponIssuer.SELLER);
        assertThat(coupon.sellerId()).isEqualTo(sellerId);
        assertThat(auditLog.entries).isEmpty();
    }

    @Test
    @DisplayName("입력 규칙을 어기면 저장하지 않는다")
    void invalid() {
        CreateCouponCommand invalid =
                new CreateCouponCommand(
                        adminId,
                        null,
                        "쿠폰",
                        CreateCouponCommand.DiscountType.RATE,
                        150,
                        null,
                        0,
                        10,
                        NOW,
                        NOW.plusSeconds(3600),
                        7);

        assertThatThrownBy(() -> create.execute(invalid))
                .isInstanceOf(InvalidCouponException.class);
        assertThat(coupons.rows).isEmpty();
        assertThat(auditLog.entries).isEmpty();
    }

    @Test
    @DisplayName("관리자는 모든 쿠폰을 중지하고 감사 로그 COUPON_STOPPED를 남긴다. 다시 중지하면 기록하지 않는다")
    void adminStops() {
        UUID couponId = create.execute(command(sellerId, sellerId));

        stop.execute(new StopCouponCommand(adminId, null, couponId));
        stop.execute(new StopCouponCommand(adminId, null, couponId));

        assertThat(coupons.rows.get(couponId).status()).isEqualTo(CouponStatus.STOPPED);
        assertThat(auditLog.entries)
                .containsExactly(
                        new AuditEntry(
                                adminId.toString(),
                                "COUPON_STOPPED",
                                "COUPON",
                                couponId.toString(),
                                null));
    }

    @Test
    @DisplayName("판매자는 자기 쿠폰만 중지한다. 다른 판매자나 플랫폼 쿠폰은 없는 쿠폰과 같다")
    void sellerStopsOwnCouponOnly() {
        UUID own = create.execute(command(sellerId, sellerId));
        UUID others = create.execute(command(UUID.randomUUID(), UUID.randomUUID()));
        UUID platform = create.execute(command(adminId, null));
        auditLog.entries.clear();

        stop.execute(new StopCouponCommand(sellerId, sellerId, own));

        assertThat(coupons.rows.get(own).status()).isEqualTo(CouponStatus.STOPPED);
        assertThat(auditLog.entries).isEmpty();
        assertThatThrownBy(() -> stop.execute(new StopCouponCommand(sellerId, sellerId, others)))
                .isInstanceOf(CouponNotFoundException.class);
        assertThatThrownBy(() -> stop.execute(new StopCouponCommand(sellerId, sellerId, platform)))
                .isInstanceOf(CouponNotFoundException.class);
        assertThatThrownBy(
                        () -> stop.execute(new StopCouponCommand(adminId, null, UUID.randomUUID())))
                .isInstanceOf(CouponNotFoundException.class);
    }

    private static CreateCouponCommand command(UUID actorId, @Nullable UUID sellerId) {
        return new CreateCouponCommand(
                actorId,
                sellerId,
                "가을 맞이 10%",
                CreateCouponCommand.DiscountType.RATE,
                10,
                5_000L,
                20_000,
                100,
                NOW,
                NOW.plusSeconds(7 * 24 * 3600),
                14);
    }
}
