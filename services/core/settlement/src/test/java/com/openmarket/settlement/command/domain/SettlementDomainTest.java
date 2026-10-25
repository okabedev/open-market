package com.openmarket.settlement.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 정산 기간, 수수료율, 정산 대상(docs/architecture/settlement.md). */
class SettlementDomainTest {

    // 2026-01-05T00:00+09:00, 월요일
    static final Instant ANCHOR = Instant.parse("2026-01-04T15:00:00Z");
    static final Duration WEEK = Duration.ofDays(7);
    static final Instant NOW = Instant.parse("2026-10-29T01:00:00Z");

    @Test
    @DisplayName("정산 기간은 기준 시각에서 길이씩 나눈 [시작, 끝)이다. 경계 시각은 다음 기간이다")
    void periods() {
        // 2026-10-26(월) 00:00 KST = 2026-10-25T15:00Z
        SettlementPeriod period =
                SettlementPeriod.of(Instant.parse("2026-10-28T07:00:00Z"), ANCHOR, WEEK);
        assertThat(period.start()).isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
        assertThat(period.end()).isEqualTo(Instant.parse("2026-11-01T15:00:00Z"));

        assertThat(SettlementPeriod.of(Instant.parse("2026-11-01T15:00:00Z"), ANCHOR, WEEK).start())
                .isEqualTo(Instant.parse("2026-11-01T15:00:00Z"));
        assertThat(
                        SettlementPeriod.of(Instant.parse("2026-11-01T14:59:59.999Z"), ANCHOR, WEEK)
                                .start())
                .isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
        assertThat(period.next().start()).isEqualTo(period.end());
    }

    @Test
    @DisplayName("기준 시각 전의 시각도 그 앞 기간에 들어가고, 짧은 길이(E2E)도 같은 규칙이다")
    void periodsBeforeAnchorAndShortLength() {
        SettlementPeriod before =
                SettlementPeriod.of(Instant.parse("2026-01-04T14:59:59Z"), ANCHOR, WEEK);
        assertThat(before.start()).isEqualTo(Instant.parse("2025-12-28T15:00:00Z"));
        assertThat(before.end()).isEqualTo(ANCHOR);

        SettlementPeriod minute =
                SettlementPeriod.of(
                        Instant.parse("2026-10-28T07:00:42Z"), ANCHOR, Duration.ofMinutes(1));
        assertThat(minute.start()).isEqualTo(Instant.parse("2026-10-28T07:00:00Z"));
        assertThat(minute.end()).isEqualTo(Instant.parse("2026-10-28T07:01:00Z"));
        assertThatThrownBy(() -> SettlementPeriod.of(NOW, ANCHOR, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("수수료율은 bp 0~10,000이다. 같은 요율로 바꾸면 바뀐 것이 없다")
    void commissionRates() {
        UUID category = UUID.randomUUID();
        CommissionRate rate = CommissionRate.create(category, 800, NOW);

        assertThat(rate.categoryId()).isEqualTo(category);
        assertThat(rate.rateBp()).isEqualTo(800);
        assertThat(rate.change(800, NOW.plusSeconds(1))).isFalse();
        assertThat(rate.updatedAt()).isEqualTo(NOW);
        assertThat(rate.change(0, NOW.plusSeconds(1))).isTrue();
        assertThat(rate.rateBp()).isZero();
        assertThat(CommissionRate.create(category, 10_000, NOW).rateBp()).isEqualTo(10_000);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 10_001})
    @DisplayName("요율이 범위 밖이면 400 SETTLEMENT_INVALID_INPUT")
    void rejectsRateOutOfRange(int rateBp) {
        assertThatThrownBy(() -> CommissionRate.create(UUID.randomUUID(), rateBp, NOW))
                .isInstanceOf(InvalidSettlementInputException.class);
        CommissionRate rate = CommissionRate.create(UUID.randomUUID(), 500, NOW);
        assertThatThrownBy(() -> rate.change(rateBp, NOW))
                .isInstanceOf(InvalidSettlementInputException.class);
    }

    @Test
    @DisplayName("정산 대상: 판매 금액, 항목별 floor 수수료, 판매자 쿠폰 부담분, 정산 금액과 받은 때의 요율을 고정한다")
    void createsItem() {
        UUID shirts = UUID.randomUUID();
        UUID books = UUID.randomUUID();
        UUID lineA = UUID.randomUUID();
        UUID lineB = UUID.randomUUID();
        SettlementItem.Purchase purchase =
                purchase(
                        2_000,
                        List.of(
                                line(lineA, shirts, 2, 39_800), // 39,800 × 8.5% = 3,383
                                line(lineB, books, 1, 12_345))); // 12,345 × 10% = 1,234.5 → 1,234
        Map<UUID, Integer> rates = Map.of(shirts, 850);
        SettlementPeriod period = SettlementPeriod.of(purchase.confirmedAt(), ANCHOR, WEEK);

        SettlementItem item =
                SettlementItem.create(
                        purchase, category -> rates.getOrDefault(category, 1_000), period, NOW);

        assertThat(item.sellerOrderId()).isEqualTo(purchase.sellerOrderId());
        assertThat(item.orderId()).isEqualTo(purchase.orderId());
        assertThat(item.sellerId()).isEqualTo(purchase.sellerId());
        assertThat(item.confirmedAt()).isEqualTo(purchase.confirmedAt());
        assertThat(item.periodStart()).isEqualTo(period.start());
        assertThat(item.periodEnd()).isEqualTo(period.end());
        assertThat(item.salesAmount()).isEqualTo(52_145);
        assertThat(item.commissionAmount()).isEqualTo(3_383 + 1_234);
        assertThat(item.sellerCouponDiscount()).isEqualTo(2_000);
        assertThat(item.settlementAmount()).isEqualTo(52_145 - 4_617 - 2_000);
        assertThat(item.isSettled()).isFalse();
        assertThat(item.lines())
                .extracting(
                        SettlementItemLine::orderLineId,
                        SettlementItemLine::commissionRateBp,
                        SettlementItemLine::commissionAmount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(lineA, 850, 3_383L),
                        org.assertj.core.groups.Tuple.tuple(lineB, 1_000, 1_234L));
        assertThat(item.lines().getFirst().productName()).isEqualTo("반팔 티셔츠");
        assertThat(item.lines().getFirst().categoryId()).isEqualTo(shirts);
        assertThat(item.lines().getFirst().quantity()).isEqualTo(2);
        assertThat(item.lines().getFirst().amount()).isEqualTo(39_800);
    }

    @Test
    @DisplayName("판매자 쿠폰이 커서 정산 금액이 0 이하여도 만든다")
    void allowsNonPositiveSettlementAmount() {
        SettlementItem.Purchase purchase =
                purchase(10_000, List.of(line(UUID.randomUUID(), UUID.randomUUID(), 1, 9_000)));

        SettlementItem item =
                SettlementItem.create(
                        purchase,
                        category -> 1_000,
                        SettlementPeriod.of(purchase.confirmedAt(), ANCHOR, WEEK),
                        NOW);

        assertThat(item.settlementAmount()).isEqualTo(9_000 - 900 - 10_000);
    }

    @Test
    @DisplayName("항목이 없거나 금액·수량·쿠폰 부담분이 음수면 만들지 않는다")
    void rejectsInvalidPurchase() {
        assertThatThrownBy(() -> purchase(0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                purchase(
                                        -1,
                                        List.of(line(UUID.randomUUID(), UUID.randomUUID(), 1, 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line(UUID.randomUUID(), UUID.randomUUID(), 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line(UUID.randomUUID(), UUID.randomUUID(), 1, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static SettlementItem.Purchase purchase(
            long sellerCouponDiscount, List<SettlementItem.Line> lines) {
        return new SettlementItem.Purchase(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-10-28T07:00:00Z"),
                sellerCouponDiscount,
                lines);
    }

    static SettlementItem.Line line(UUID orderLineId, UUID categoryId, int quantity, long amount) {
        return new SettlementItem.Line(
                orderLineId, UUID.randomUUID(), "반팔 티셔츠", categoryId, quantity, amount);
    }
}
