package com.openmarket.settlement.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 정산 확정과 지급 처리(docs/architecture/settlement.md "정산 확정", "지급 처리"). */
class SettlementTest {

    static final Instant ANCHOR = Instant.parse("2026-01-04T15:00:00Z");
    static final Instant CONFIRMED_AT = Instant.parse("2026-10-28T07:00:00Z");
    static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");
    static final SettlementPeriod PERIOD =
            SettlementPeriod.of(CONFIRMED_AT, ANCHOR, Duration.ofDays(7));

    final UUID sellerId = UUID.randomUUID();

    @Test
    @DisplayName("판매자·기간의 대상을 모아 정산을 확정한다. 금액은 대상 금액의 합이고 대상에 정산 ID가 남는다")
    void confirms() {
        SettlementItem first = item(sellerId, 30_000, 0);
        SettlementItem second = item(sellerId, 10_000, 5_000);

        Settlement settlement = Settlement.confirm(sellerId, PERIOD, List.of(first, second), NOW);

        assertThat(settlement.sellerId()).isEqualTo(sellerId);
        assertThat(settlement.periodStart()).isEqualTo(PERIOD.start());
        assertThat(settlement.periodEnd()).isEqualTo(PERIOD.end());
        assertThat(settlement.itemCount()).isEqualTo(2);
        assertThat(settlement.salesAmount()).isEqualTo(40_000);
        assertThat(settlement.commissionAmount()).isEqualTo(4_000);
        assertThat(settlement.sellerCouponDiscount()).isEqualTo(5_000);
        assertThat(settlement.amount()).isEqualTo(40_000 - 4_000 - 5_000);
        assertThat(settlement.status()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(settlement.confirmedAt()).isEqualTo(NOW);
        assertThat(settlement.paidOutAt()).isNull();
        assertThat(first.settlementId()).isEqualTo(settlement.id());
        assertThat(second.isSettled()).isTrue();
    }

    @Test
    @DisplayName("대상이 없거나, 다른 판매자·기간이거나, 이미 정산된 대상이면 확정하지 않는다")
    void rejectsInvalidItems() {
        SettlementItem settled = item(sellerId, 10_000, 0);
        Settlement.confirm(sellerId, PERIOD, List.of(settled), NOW);

        assertThatThrownBy(() -> Settlement.confirm(sellerId, PERIOD, List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                Settlement.confirm(
                                        sellerId,
                                        PERIOD,
                                        List.of(item(UUID.randomUUID(), 10_000, 0)),
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                Settlement.confirm(
                                        sellerId,
                                        PERIOD.next(),
                                        List.of(item(sellerId, 10_000, 0)),
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Settlement.confirm(sellerId, PERIOD, List.of(settled), NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("지급 처리하면 PAID_OUT이고 지급 시각과 관리자를 남긴다. 다시 하면 바뀐 것이 없다")
    void paysOut() {
        Settlement settlement =
                Settlement.confirm(sellerId, PERIOD, List.of(item(sellerId, 10_000, 0)), NOW);
        UUID adminId = UUID.randomUUID();
        Instant later = NOW.plus(Duration.ofDays(1));

        assertThat(settlement.payOut(adminId, later)).isTrue();
        assertThat(settlement.status()).isEqualTo(SettlementStatus.PAID_OUT);
        assertThat(settlement.paidOutAt()).isEqualTo(later);
        assertThat(settlement.paidOutBy()).isEqualTo(adminId);
        assertThat(settlement.payOut(UUID.randomUUID(), later.plusSeconds(1))).isFalse();
        assertThat(settlement.paidOutBy()).isEqualTo(adminId);
    }

    @Test
    @DisplayName("SettlementConfirmed와 SettlementPaidOut은 정산의 금액과 시각을 싣는다")
    void events() {
        Settlement settlement =
                Settlement.confirm(sellerId, PERIOD, List.of(item(sellerId, 10_000, 1_000)), NOW);
        settlement.payOut(UUID.randomUUID(), NOW.plusSeconds(60));

        assertThat(SettlementConfirmed.of(settlement))
                .isEqualTo(
                        new SettlementConfirmed(
                                settlement.id(),
                                sellerId,
                                PERIOD.start(),
                                PERIOD.end(),
                                10_000,
                                1_000,
                                1_000,
                                8_000,
                                NOW));
        assertThat(SettlementPaidOut.of(settlement))
                .isEqualTo(
                        new SettlementPaidOut(
                                settlement.id(), sellerId, 8_000, NOW.plusSeconds(60)));
    }

    static SettlementItem item(UUID sellerId, long amount, long sellerCouponDiscount) {
        return SettlementItem.create(
                new SettlementItem.Purchase(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        sellerId,
                        CONFIRMED_AT,
                        sellerCouponDiscount,
                        List.of(
                                new SettlementItem.Line(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        "반팔 티셔츠",
                                        UUID.randomUUID(),
                                        1,
                                        amount))),
                category -> 1_000,
                PERIOD,
                CONFIRMED_AT);
    }
}
