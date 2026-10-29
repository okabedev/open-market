package com.openmarket.settlement.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.settlement.command.application.ConfirmSettlementCommand.AdminRun;
import com.openmarket.settlement.command.domain.SellerLock;
import com.openmarket.settlement.command.domain.Settlement;
import com.openmarket.settlement.command.domain.SettlementConfirmed;
import com.openmarket.settlement.command.domain.SettlementItem;
import com.openmarket.settlement.command.domain.SettlementItemRepository;
import com.openmarket.settlement.command.domain.SettlementPeriod;
import com.openmarket.settlement.command.domain.SettlementRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자·기간 하나의 정산을 확정한다(STL-2, docs/architecture/settlement.md "정산 확정"). 판매자를 잠그고, 확정되지 않은 대상을 모아 정산을
 * 만들고, 같은 트랜잭션에서 {@code SettlementConfirmed}를 outbox에 쓴다. 이미 확정한 판매자·기간이거나 대상이 없으면 아무것도 하지 않는다
 * (STL-4).
 *
 * <p>관리자 즉시 실행으로 확정하면 같은 트랜잭션에서 감사 로그 {@value #AUDIT_ACTION}(대상은 정산, 부가 설명은 실행 ID와 정산 금액)를 남긴다
 * (NFR-8).
 */
@Service
public class ConfirmSettlementUseCase {

    public static final String TOPIC = "settlement.settlement";
    static final String AUDIT_ACTION = "SETTLEMENT_CONFIRMED";
    static final String AUDIT_TARGET = "SETTLEMENT";

    private final SettlementItemRepository items;
    private final SettlementRepository settlements;
    private final SellerLock sellerLock;
    private final Outbox outbox;
    private final AuditLog auditLog;
    private final Clock clock;

    public ConfirmSettlementUseCase(
            SettlementItemRepository items,
            SettlementRepository settlements,
            SellerLock sellerLock,
            Outbox outbox,
            AuditLog auditLog,
            Clock clock) {
        this.items = items;
        this.settlements = settlements;
        this.sellerLock = sellerLock;
        this.outbox = outbox;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /** 확정했으면 true. */
    @Transactional
    public boolean execute(ConfirmSettlementCommand command) {
        sellerLock.lock(command.sellerId());
        if (settlements.existsBySellerIdAndPeriodStart(command.sellerId(), command.periodStart())) {
            return false;
        }
        List<SettlementItem> unsettled =
                items.findUnsettled(command.sellerId(), command.periodStart());
        if (unsettled.isEmpty()) {
            return false;
        }
        SettlementItem first = unsettled.getFirst();
        Settlement settlement =
                Settlement.confirm(
                        command.sellerId(),
                        new SettlementPeriod(first.periodStart(), first.periodEnd()),
                        unsettled,
                        clock.instant());
        settlements.save(settlement);
        unsettled.forEach(items::save);
        outbox.append(
                TOPIC,
                settlement.id().toString(),
                SettlementConfirmed.TYPE,
                SettlementConfirmed.of(settlement));
        AdminRun adminRun = command.adminRun();
        if (adminRun != null) {
            auditLog.record(
                    new AuditEntry(
                            adminRun.adminId().toString(),
                            AUDIT_ACTION,
                            AUDIT_TARGET,
                            settlement.id().toString(),
                            "실행 " + adminRun.runId() + ", " + settlement.amount() + "원"));
        }
        return true;
    }
}
