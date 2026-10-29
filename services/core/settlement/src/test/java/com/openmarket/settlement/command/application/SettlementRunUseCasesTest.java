package com.openmarket.settlement.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.settlement.command.domain.Settlement;
import com.openmarket.settlement.command.domain.SettlementConfirmed;
import com.openmarket.settlement.command.domain.SettlementItem;
import com.openmarket.settlement.command.domain.SettlementNotFoundException;
import com.openmarket.settlement.command.domain.SettlementPaidOut;
import com.openmarket.settlement.command.domain.SettlementStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 정산 확정 배치와 지급 처리(docs/architecture/settlement.md "정산 확정", "지급 처리"). */
class SettlementRunUseCasesTest {

    static final String ANCHOR = "2026-01-05T00:00:00+09:00";
    // 2026-10-26(월) 00:00 KST부터의 기간이 끝나고(11-01T15:00Z) 마감 지연 3시간이 지난 뒤
    static final Instant NOW = Instant.parse("2026-11-01T20:00:00Z");
    static final Instant LAST_WEEK = Instant.parse("2026-10-28T07:00:00Z");
    static final Instant TWO_WEEKS_AGO = Instant.parse("2026-10-21T07:00:00Z");
    static final Instant THIS_WEEK = Instant.parse("2026-11-01T16:00:00Z");

    SettlementFakes.Items items;
    SettlementFakes.Settlements settlements;
    SettlementFakes.Rates rates;
    SettlementFakes.RecordingSellerLock sellerLock;
    SettlementFakes.RecordingOutbox outbox;
    SettlementFakes.RecordingAuditLog auditLog;
    CreateSettlementItemUseCase createItem;
    ConfirmSettlementUseCase confirm;
    RunSettlementUseCase run;
    PayOutSettlementUseCase payOut;

    UUID sellerA = UUID.randomUUID();
    UUID sellerB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        items = new SettlementFakes.Items();
        settlements = new SettlementFakes.Settlements();
        rates = new SettlementFakes.Rates();
        sellerLock = new SettlementFakes.RecordingSellerLock();
        outbox = new SettlementFakes.RecordingOutbox();
        auditLog = new SettlementFakes.RecordingAuditLog();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        createItem =
                new CreateSettlementItemUseCase(
                        items,
                        settlements,
                        rates,
                        sellerLock,
                        clock,
                        ANCHOR,
                        Duration.ofDays(7),
                        1_000);
        confirm =
                new ConfirmSettlementUseCase(
                        items, settlements, sellerLock, outbox, auditLog, clock);
        run = new RunSettlementUseCase(items, confirm, auditLog, clock, Duration.ofHours(3));
        payOut = new PayOutSettlementUseCase(settlements, outbox, auditLog, clock);
    }

    @Test
    @DisplayName("마감 지연이 지난 기간의 대상을 판매자·기간마다 오래된 순으로 확정하고 SettlementConfirmed를 낸다")
    void confirmsClosedPeriods() {
        purchase(sellerA, LAST_WEEK, 30_000);
        purchase(sellerA, LAST_WEEK, 10_000);
        purchase(sellerB, LAST_WEEK, 20_000);
        purchase(sellerA, TWO_WEEKS_AGO, 5_000);
        purchase(sellerA, THIS_WEEK, 7_000);

        int confirmed = run.execute();

        assertThat(confirmed).isEqualTo(3);
        assertThat(settlements.rows.values())
                .extracting(Settlement::sellerId, Settlement::salesAmount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(sellerA, 5_000L),
                        org.assertj.core.groups.Tuple.tuple(sellerA, 40_000L),
                        org.assertj.core.groups.Tuple.tuple(sellerB, 20_000L));
        // 오래된 기간부터 확정한다. 같은 기간 안에서는 판매자 ID 순이다
        List<Settlement> inOrder = settlements.rows.values().stream().toList();
        assertThat(inOrder.getFirst().salesAmount()).isEqualTo(5_000);
        Settlement lastWeekA =
                inOrder.stream()
                        .filter(s -> s.sellerId().equals(sellerA) && s.salesAmount() == 40_000)
                        .findFirst()
                        .orElseThrow();
        int lastWeekAIndex = inOrder.indexOf(lastWeekA);
        assertThat(lastWeekA.periodStart()).isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
        assertThat(lastWeekA.amount()).isEqualTo(40_000 - 4_000);
        assertThat(outbox.types()).containsOnly(SettlementConfirmed.TYPE).hasSize(3);
        assertThat(outbox.events.get(lastWeekAIndex).aggregateType())
                .isEqualTo("settlement.settlement");
        assertThat(outbox.events.get(lastWeekAIndex).aggregateId())
                .isEqualTo(lastWeekA.id().toString());
        assertThat(outbox.events.get(lastWeekAIndex).payload())
                .isEqualTo(SettlementConfirmed.of(lastWeekA));
        // 이번 주 대상은 아직 확정하지 않는다
        assertThat(items.all())
                .filteredOn(i -> i.confirmedAt().equals(THIS_WEEK))
                .allMatch(i -> !i.isSettled());
        assertThat(sellerLock.locked).contains(sellerA, sellerB);
    }

    @Test
    @DisplayName(
            "관리자가 즉시 실행하면 확정한 정산마다 SETTLEMENT_CONFIRMED를, 끝나면 SETTLEMENT_RUN_EXECUTED(확정 건수)를 남긴다. 정기 실행은 남기지 않는다(NFR-8)")
    void adminRunIsAudited() {
        UUID adminId = UUID.randomUUID();
        purchase(sellerA, LAST_WEEK, 30_000);
        purchase(sellerB, LAST_WEEK, 20_000);

        int confirmed = run.execute(new RunSettlementCommand(adminId));
        run.execute();

        assertThat(confirmed).isEqualTo(2);
        assertThat(auditLog.entries)
                .hasSize(3)
                .allMatch(e -> e.actorId().equals(adminId.toString()));
        AuditEntry summary = auditLog.entries.getLast();
        assertThat(summary.action()).isEqualTo("SETTLEMENT_RUN_EXECUTED");
        assertThat(summary.targetType()).isEqualTo("SETTLEMENT_RUN");
        assertThat(summary.detail()).isEqualTo("확정 2건");
        List<AuditEntry> perSettlement = auditLog.entries.subList(0, 2);
        assertThat(perSettlement)
                .allSatisfy(
                        entry -> {
                            assertThat(entry.action()).isEqualTo("SETTLEMENT_CONFIRMED");
                            assertThat(entry.targetType()).isEqualTo("SETTLEMENT");
                            assertThat(entry.detail()).startsWith("실행 " + summary.targetId());
                        })
                .extracting(AuditEntry::targetId)
                .containsExactlyInAnyOrderElementsOf(
                        settlements.rows.values().stream()
                                .map(settlement -> settlement.id().toString())
                                .toList());
    }

    @Test
    @DisplayName("관리자 즉시 실행이 도중에 실패해도 이미 확정한 정산과 실행 요약의 감사 로그가 남는다")
    void failedAdminRunIsStillAudited() {
        UUID adminId = UUID.randomUUID();
        purchase(sellerA, TWO_WEEKS_AGO, 30_000);
        purchase(sellerB, LAST_WEEK, 20_000);
        sellerLock.failFor = sellerB;

        assertThatThrownBy(() -> run.execute(new RunSettlementCommand(adminId)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(auditLog.entries)
                .extracting(AuditEntry::action)
                .containsExactly("SETTLEMENT_CONFIRMED", "SETTLEMENT_RUN_EXECUTED");
        assertThat(auditLog.entries.getFirst().targetId())
                .isEqualTo(settlements.rows.values().iterator().next().id().toString());
        assertThat(auditLog.entries.getLast().detail()).isEqualTo("확정 1건(오류로 중단)");
    }

    @Test
    @DisplayName("다시 돌려도 이미 확정한 판매자·기간은 넘겨 정산 금액이 같다(STL-4)")
    void runsIdempotently() {
        purchase(sellerA, LAST_WEEK, 30_000);
        run.execute();
        long amount = settlements.rows.values().iterator().next().amount();

        int again = run.execute();
        boolean direct =
                confirm.execute(
                        new ConfirmSettlementCommand(
                                sellerA, Instant.parse("2026-10-25T15:00:00Z")));

        assertThat(again).isZero();
        assertThat(direct).isFalse();
        assertThat(settlements.rows).hasSize(1);
        assertThat(settlements.rows.values().iterator().next().amount()).isEqualTo(amount);
        assertThat(outbox.events).hasSize(1);
    }

    @Test
    @DisplayName("판매자의 그 기간이 이미 확정됐으면 늦게 온 대상은 다음 기간에 넣는다")
    void movesLateItemToNextPeriod() {
        purchase(sellerA, LAST_WEEK, 30_000);
        run.execute();

        SettlementItem late = purchase(sellerA, LAST_WEEK, 9_000);

        assertThat(late.periodStart()).isEqualTo(Instant.parse("2026-11-01T15:00:00Z"));
        assertThat(late.periodEnd()).isEqualTo(Instant.parse("2026-11-08T15:00:00Z"));
        assertThat(run.execute()).isZero();
        assertThat(settlements.rows.values().iterator().next().salesAmount()).isEqualTo(30_000);
        // 다른 판매자의 같은 기간은 그대로다
        assertThat(purchase(sellerB, LAST_WEEK, 1_000).periodStart())
                .isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
    }

    @Test
    @DisplayName("지급 처리하면 PAID_OUT, SettlementPaidOut과 감사 로그 SETTLEMENT_PAID_OUT. 다시 하면 아무것도 없다")
    void paysOut() {
        purchase(sellerA, LAST_WEEK, 30_000);
        run.execute();
        Settlement settlement = settlements.rows.values().iterator().next();
        outbox.events.clear();
        UUID adminId = UUID.randomUUID();

        payOut.execute(new PayOutSettlementCommand(adminId, settlement.id()));
        payOut.execute(new PayOutSettlementCommand(adminId, settlement.id()));

        assertThat(settlement.status()).isEqualTo(SettlementStatus.PAID_OUT);
        assertThat(settlement.paidOutAt()).isEqualTo(NOW);
        assertThat(outbox.types()).containsExactly(SettlementPaidOut.TYPE);
        assertThat(outbox.events.getFirst().payload()).isEqualTo(SettlementPaidOut.of(settlement));
        assertThat(auditLog.entries)
                .containsExactly(
                        new AuditEntry(
                                adminId.toString(),
                                "SETTLEMENT_PAID_OUT",
                                "SETTLEMENT",
                                settlement.id().toString(),
                                "27000원"));
    }

    @Test
    @DisplayName("없는 정산을 지급 처리하면 404 SETTLEMENT_NOT_FOUND")
    void rejectsUnknownSettlement() {
        assertThatThrownBy(
                        () ->
                                payOut.execute(
                                        new PayOutSettlementCommand(
                                                UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(SettlementNotFoundException.class);
    }

    private SettlementItem purchase(UUID sellerId, Instant confirmedAt, long amount) {
        UUID sellerOrderId = UUID.randomUUID();
        createItem.execute(
                new CreateSettlementItemCommand(
                        UUID.randomUUID(),
                        sellerOrderId,
                        sellerId,
                        confirmedAt,
                        0,
                        List.of(
                                new CreateSettlementItemCommand.Line(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        "반팔 티셔츠",
                                        UUID.randomUUID(),
                                        1,
                                        amount))));
        return items.rows.get(sellerOrderId);
    }
}
