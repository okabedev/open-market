package com.openmarket.settlement.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.settlement.command.domain.InvalidSettlementInputException;
import com.openmarket.settlement.command.domain.SettlementItem;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementUseCasesTest {

    static final Instant NOW = Instant.parse("2026-10-29T01:00:00Z");
    static final String ANCHOR = "2026-01-05T00:00:00+09:00";

    SettlementFakes.Rates rates;
    SettlementFakes.Items items;
    SettlementFakes.RecordingAuditLog auditLog;
    SetCommissionRateUseCase setRate;
    RemoveCommissionRateUseCase removeRate;
    CreateSettlementItemUseCase createItem;

    UUID adminId = UUID.randomUUID();
    UUID shirts = UUID.randomUUID();
    UUID books = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        rates = new SettlementFakes.Rates();
        items = new SettlementFakes.Items();
        auditLog = new SettlementFakes.RecordingAuditLog();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        setRate = new SetCommissionRateUseCase(rates, auditLog, clock, 1_000);
        removeRate = new RemoveCommissionRateUseCase(rates, auditLog, 1_000);
        createItem =
                new CreateSettlementItemUseCase(
                        items,
                        new SettlementFakes.Settlements(),
                        rates,
                        new SettlementFakes.RecordingSellerLock(),
                        clock,
                        ANCHOR,
                        Duration.ofDays(7),
                        1_000);
    }

    @Test
    @DisplayName("요율을 정하면 저장하고 감사 로그에 이전 → 새 요율을 남긴다. 같은 요율이면 감사 로그가 없다")
    void setsRate() {
        setRate.execute(new SetCommissionRateCommand(adminId, shirts, 850));
        setRate.execute(new SetCommissionRateCommand(adminId, shirts, 850));
        setRate.execute(new SetCommissionRateCommand(adminId, shirts, 700));

        assertThat(rates.rows.get(shirts).rateBp()).isEqualTo(700);
        assertThat(auditLog.entries)
                .containsExactly(
                        new AuditEntry(
                                adminId.toString(),
                                "COMMISSION_RATE_CHANGED",
                                "CATEGORY",
                                shirts.toString(),
                                "기본(1000bp) → 850bp"),
                        new AuditEntry(
                                adminId.toString(),
                                "COMMISSION_RATE_CHANGED",
                                "CATEGORY",
                                shirts.toString(),
                                "850bp → 700bp"));
    }

    @Test
    @DisplayName("범위 밖 요율은 400 SETTLEMENT_INVALID_INPUT이고 저장하지 않는다")
    void rejectsRate() {
        assertThatThrownBy(
                        () ->
                                setRate.execute(
                                        new SetCommissionRateCommand(adminId, shirts, 10_001)))
                .isInstanceOf(InvalidSettlementInputException.class);
        assertThat(rates.rows).isEmpty();
        assertThat(auditLog.entries).isEmpty();
    }

    @Test
    @DisplayName("요율을 지우면 기본 요율로 돌아가고 감사 로그를 남긴다. 없으면 아무것도 하지 않는다")
    void removesRate() {
        setRate.execute(new SetCommissionRateCommand(adminId, shirts, 850));
        auditLog.entries.clear();

        removeRate.execute(new RemoveCommissionRateCommand(adminId, shirts));
        removeRate.execute(new RemoveCommissionRateCommand(adminId, shirts));

        assertThat(rates.rows).isEmpty();
        assertThat(auditLog.entries)
                .extracting(AuditEntry::detail)
                .containsExactly("850bp → 기본(1000bp)");
    }

    @Test
    @DisplayName("구매확정으로 정산 대상을 만든다. 요율이 없는 카테고리는 기본 요율이고, 같은 하위 주문은 한 번만 만든다")
    void createsItem() {
        setRate.execute(new SetCommissionRateCommand(adminId, shirts, 850));
        UUID sellerOrderId = UUID.randomUUID();
        CreateSettlementItemCommand command = confirmed(sellerOrderId);

        createItem.execute(command);
        setRate.execute(new SetCommissionRateCommand(adminId, shirts, 500));
        createItem.execute(command);

        assertThat(items.rows).containsOnlyKeys(sellerOrderId);
        SettlementItem item = items.rows.get(sellerOrderId);
        // 39,800 × 8.5% = 3,383, 10,000 × 10% = 1,000
        assertThat(item.salesAmount()).isEqualTo(49_800);
        assertThat(item.commissionAmount()).isEqualTo(4_383);
        assertThat(item.settlementAmount()).isEqualTo(49_800 - 4_383 - 2_000);
        // 2026-10-28T07:00Z는 2026-10-26(월) 00:00 KST부터의 기간이다
        assertThat(item.periodStart()).isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
        assertThat(item.periodEnd()).isEqualTo(Instant.parse("2026-11-01T15:00:00Z"));
        assertThat(item.lines().getFirst().commissionRateBp()).isEqualTo(850);
    }

    private CreateSettlementItemCommand confirmed(UUID sellerOrderId) {
        return new CreateSettlementItemCommand(
                UUID.randomUUID(),
                sellerOrderId,
                UUID.randomUUID(),
                Instant.parse("2026-10-28T07:00:00Z"),
                2_000,
                List.of(
                        new CreateSettlementItemCommand.Line(
                                UUID.randomUUID(), UUID.randomUUID(), "반팔 티셔츠", shirts, 2, 39_800),
                        new CreateSettlementItemCommand.Line(
                                UUID.randomUUID(), UUID.randomUUID(), "소설책", books, 1, 10_000)));
    }
}
