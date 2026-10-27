package com.openmarket.promotion.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.promotion.command.domain.Coupon;
import com.openmarket.promotion.command.domain.CouponNotFoundException;
import com.openmarket.promotion.command.domain.CouponNotIssuingException;
import com.openmarket.promotion.command.domain.CouponSoldOutException;
import com.openmarket.promotion.command.domain.CouponTerms;
import com.openmarket.promotion.command.domain.DiscountType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/** 선착순 발급(docs/architecture/promotion.md "선착순 발급"). */
class IssueCouponUseCaseTest {

    static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");

    final PromotionFakes.Coupons coupons = new PromotionFakes.Coupons();
    final PromotionFakes.IssuedCoupons issued = new PromotionFakes.IssuedCoupons();
    final PromotionFakes.Gate gate = new PromotionFakes.Gate();
    final IssueCouponUseCase issue =
            new IssueCouponUseCase(
                    coupons,
                    coupons::findById,
                    issued,
                    gate,
                    TransactionOperations.withoutTransaction(),
                    Clock.fixed(NOW, ZoneOffset.UTC));

    final Coupon coupon = coupons.save(Coupon.platform(terms(2), NOW.minusSeconds(7200)));

    @Test
    @DisplayName("처음 내려받으면 발급 쿠폰을 만들고, 다시 눌러도 같은 쿠폰을 준다")
    void issueOnce() {
        UUID memberId = UUID.randomUUID();

        IssueCouponUseCase.Result first = issue.execute(command(memberId));
        IssueCouponUseCase.Result second = issue.execute(command(memberId));

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.issuedCouponId()).isEqualTo(first.issuedCouponId());
        assertThat(issued.countByCouponId(coupon.id())).isEqualTo(1);
        assertThat(coupons.issuedCount(coupon.id())).isEqualTo(1);
        assertThat(gate.stock.get(coupon.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("수량을 다 쓰면 409 PROMOTION_COUPON_SOLD_OUT")
    void soldOut() {
        issue.execute(command(UUID.randomUUID()));
        issue.execute(command(UUID.randomUUID()));

        assertThatThrownBy(() -> issue.execute(command(UUID.randomUUID())))
                .isInstanceOf(CouponSoldOutException.class)
                .hasFieldOrPropertyWithValue("code", "PROMOTION_COUPON_SOLD_OUT");
        assertThat(issued.countByCouponId(coupon.id())).isEqualTo(2);
    }

    @Test
    @DisplayName("중지했거나 발급 기간 밖이면 Redis를 부르지 않고 409 PROMOTION_COUPON_NOT_ISSUING")
    void notIssuing() {
        coupon.stop(NOW);
        gate.down = true;

        assertThatThrownBy(() -> issue.execute(command(UUID.randomUUID())))
                .isInstanceOf(CouponNotIssuingException.class);
        assertThatThrownBy(
                        () ->
                                issue.execute(
                                        new IssueCouponCommand(
                                                UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    @DisplayName(
            "발급 조회가 중지 전 쿠폰을 주어도(짧은 캐시) DB 저장에서 중지를 확인해 409 PROMOTION_COUPON_NOT_ISSUING이고 차지를 되돌린다")
    void staleIssuableCouponIsCheckedByDb() {
        UUID memberId = UUID.randomUUID();
        coupons.stoppedInDb.add(coupon.id());

        assertThatThrownBy(() -> issue.execute(command(memberId)))
                .isInstanceOf(CouponNotIssuingException.class);

        assertThat(issued.rows).isEmpty();
        assertThat(gate.stock.get(coupon.id())).isZero();
        assertThat(gate.issued.get(coupon.id())).doesNotContain(memberId);
    }

    @Test
    @DisplayName("Redis 키가 없으면 DB 발급 행으로 다시 만든 뒤 발급한다")
    void rebuildsKeys() {
        UUID first = UUID.randomUUID();
        issue.execute(command(first));
        gate.flush();
        gate.loads = 0;

        IssueCouponUseCase.Result again = issue.execute(command(first));
        issue.execute(command(UUID.randomUUID()));

        assertThat(again.created()).isFalse();
        assertThat(gate.loads).isEqualTo(1);
        assertThat(gate.stock.get(coupon.id())).isEqualTo(2);
        assertThatThrownBy(() -> issue.execute(command(UUID.randomUUID())))
                .isInstanceOf(CouponSoldOutException.class);
    }

    @Test
    @DisplayName("Redis를 부를 수 없으면 503 PROMOTION_UNAVAILABLE이고 DB만으로 발급하지 않는다")
    void redisDown() {
        gate.down = true;

        assertThatThrownBy(() -> issue.execute(command(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PROMOTION_UNAVAILABLE");
        assertThat(issued.rows).isEmpty();
    }

    @Test
    @DisplayName("DB 저장이 실패하면 Redis에서 차지한 것을 되돌린다")
    void releasesOnDbFailure() {
        UUID memberId = UUID.randomUUID();
        issued.insertFailure = new IllegalStateException("db down");

        assertThatThrownBy(() -> issue.execute(command(memberId)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(gate.stock.get(coupon.id())).isZero();
        assertThat(gate.issued.get(coupon.id())).doesNotContain(memberId);
        issued.insertFailure = null;
        assertThat(issue.execute(command(memberId)).created()).isTrue();
    }

    @Test
    @DisplayName("Redis가 수량을 적게 셌어도 DB 발급 수가 수량을 넘지 않는다. 그 뒤 Redis는 매진으로 맞춘다")
    void dbGuardsQuantity() {
        issue.execute(command(UUID.randomUUID()));
        issue.execute(command(UUID.randomUUID()));
        // 키를 다시 만드는 사이 끼어든 요청처럼 Redis 발급 수가 DB보다 적다.
        gate.stock.put(coupon.id(), 0);

        assertThatThrownBy(() -> issue.execute(command(UUID.randomUUID())))
                .isInstanceOf(CouponSoldOutException.class);

        assertThat(issued.countByCouponId(coupon.id())).isEqualTo(2);
        assertThat(gate.stock.get(coupon.id())).isEqualTo(2);
    }

    @Test
    @DisplayName("Redis 집합에는 있는데 DB 행이 없으면(차지 뒤 저장 전에 죽음) DB에 저장해 준다")
    void claimedWithoutRow() {
        UUID memberId = UUID.randomUUID();
        gate.loadAndClaim(coupon.id(), List.of(), memberId, 2, NOW.plusSeconds(3600));

        IssueCouponUseCase.Result result = issue.execute(command(memberId));

        assertThat(result.created()).isTrue();
        assertThat(gate.stock.get(coupon.id())).isEqualTo(1);
        assertThat(coupons.issuedCount(coupon.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("Redis 집합에서 빠졌지만 이미 발급한 회원은 새로 만들지 않고 Redis 발급 수만 되돌린다")
    void alreadyIssuedButMissingInRedis() {
        UUID memberId = UUID.randomUUID();
        IssueCouponUseCase.Result first = issue.execute(command(memberId));
        gate.issued.get(coupon.id()).remove(memberId);

        IssueCouponUseCase.Result again = issue.execute(command(memberId));

        assertThat(again.created()).isFalse();
        assertThat(again.issuedCouponId()).isEqualTo(first.issuedCouponId());
        assertThat(gate.stock.get(coupon.id())).isEqualTo(1);
        assertThat(coupons.issuedCount(coupon.id())).isEqualTo(1);
    }

    private IssueCouponCommand command(UUID memberId) {
        return new IssueCouponCommand(memberId, coupon.id());
    }

    private static CouponTerms terms(int quantity) {
        return new CouponTerms(
                "선착순 2장",
                DiscountType.FIXED,
                3_000,
                null,
                0,
                quantity,
                NOW.minusSeconds(3600),
                NOW.plus(Duration.ofDays(7)),
                7);
    }
}
