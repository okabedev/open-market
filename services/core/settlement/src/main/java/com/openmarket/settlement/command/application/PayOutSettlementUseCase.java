package com.openmarket.settlement.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.settlement.command.domain.Settlement;
import com.openmarket.settlement.command.domain.SettlementNotFoundException;
import com.openmarket.settlement.command.domain.SettlementPaidOut;
import com.openmarket.settlement.command.domain.SettlementRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 확정된 정산을 지급 완료로 기록한다(STL-3, ADM-3, docs/architecture/settlement.md "지급 처리"). 실제로 송금하지 않는다. 정산
 * 행을 잠그고 바꾸며, {@code SettlementPaidOut}과 감사 로그 {@code SETTLEMENT_PAID_OUT}을 남긴다. 이미 지급했으면 아무것도 하지
 * 않는다. 없으면 404 {@code SETTLEMENT_NOT_FOUND}.
 */
@Service
public class PayOutSettlementUseCase {

    private final SettlementRepository settlements;
    private final Outbox outbox;
    private final AuditLog auditLog;
    private final Clock clock;

    public PayOutSettlementUseCase(
            SettlementRepository settlements, Outbox outbox, AuditLog auditLog, Clock clock) {
        this.settlements = settlements;
        this.outbox = outbox;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public void execute(PayOutSettlementCommand command) {
        Settlement settlement =
                settlements
                        .findForUpdateById(command.settlementId())
                        .orElseThrow(SettlementNotFoundException::new);
        if (!settlement.payOut(command.adminId(), clock.instant())) {
            return;
        }
        settlements.save(settlement);
        outbox.append(
                ConfirmSettlementUseCase.TOPIC,
                settlement.id().toString(),
                SettlementPaidOut.TYPE,
                SettlementPaidOut.of(settlement));
        auditLog.record(
                new AuditEntry(
                        command.adminId().toString(),
                        "SETTLEMENT_PAID_OUT",
                        "SETTLEMENT",
                        settlement.id().toString(),
                        settlement.amount() + "원"));
    }
}
