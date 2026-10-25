package com.openmarket.settlement.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.settlement.command.domain.CommissionRate;
import com.openmarket.settlement.command.domain.CommissionRateRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 카테고리의 수수료율을 지워 기본 요율로 돌린다(docs/architecture/settlement.md "수수료율"). 감사 로그 {@code
 * COMMISSION_RATE_CHANGED}를 남긴다. 요율이 없으면 아무것도 하지 않는다.
 */
@Service
public class RemoveCommissionRateUseCase {

    private final CommissionRateRepository rates;
    private final AuditLog auditLog;
    private final int defaultRateBp;

    public RemoveCommissionRateUseCase(
            CommissionRateRepository rates,
            AuditLog auditLog,
            @Value("${openmarket.settlement.default-commission-rate:1000}") int defaultRateBp) {
        this.rates = rates;
        this.auditLog = auditLog;
        this.defaultRateBp = CommissionRate.checked(defaultRateBp);
    }

    @Transactional
    public void execute(RemoveCommissionRateCommand command) {
        Optional<CommissionRate> existing = rates.findForUpdateById(command.categoryId());
        if (existing.isEmpty()) {
            return;
        }
        rates.delete(existing.get());
        auditLog.record(
                new AuditEntry(
                        command.adminId().toString(),
                        SetCommissionRateUseCase.AUDIT_ACTION,
                        SetCommissionRateUseCase.AUDIT_TARGET,
                        command.categoryId().toString(),
                        existing.get().rateBp()
                                + "bp → "
                                + SetCommissionRateUseCase.defaultLabel(defaultRateBp)));
    }
}
