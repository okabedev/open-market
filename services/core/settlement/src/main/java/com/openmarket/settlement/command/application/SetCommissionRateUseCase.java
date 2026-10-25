package com.openmarket.settlement.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.settlement.command.domain.CommissionRate;
import com.openmarket.settlement.command.domain.CommissionRateRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 카테고리의 수수료율을 정한다(docs/architecture/settlement.md "수수료율"). 감사 로그 {@code
 * COMMISSION_RATE_CHANGED}에 이전 → 새 요율을 남긴다. 같은 요율이면 아무것도 하지 않는다. 바꾼 요율은 그 뒤에 생기는 정산 대상부터 쓴다.
 */
@Service
public class SetCommissionRateUseCase {

    static final String AUDIT_ACTION = "COMMISSION_RATE_CHANGED";
    static final String AUDIT_TARGET = "CATEGORY";

    private final CommissionRateRepository rates;
    private final AuditLog auditLog;
    private final Clock clock;
    private final int defaultRateBp;

    public SetCommissionRateUseCase(
            CommissionRateRepository rates,
            AuditLog auditLog,
            Clock clock,
            @Value("${openmarket.settlement.default-commission-rate:1000}") int defaultRateBp) {
        this.rates = rates;
        this.auditLog = auditLog;
        this.clock = clock;
        this.defaultRateBp = CommissionRate.checked(defaultRateBp);
    }

    @Transactional
    public void execute(SetCommissionRateCommand command) {
        CommissionRate.checked(command.rateBp());
        Instant now = clock.instant();
        Optional<CommissionRate> existing = rates.findForUpdateById(command.categoryId());
        String before;
        if (existing.isEmpty()) {
            rates.save(CommissionRate.create(command.categoryId(), command.rateBp(), now));
            before = defaultLabel(defaultRateBp);
        } else {
            int previous = existing.get().rateBp();
            if (!existing.get().change(command.rateBp(), now)) {
                return;
            }
            rates.save(existing.get());
            before = previous + "bp";
        }
        auditLog.record(
                new AuditEntry(
                        command.adminId().toString(),
                        AUDIT_ACTION,
                        AUDIT_TARGET,
                        command.categoryId().toString(),
                        before + " → " + command.rateBp() + "bp"));
    }

    static String defaultLabel(int defaultRateBp) {
        return "기본(" + defaultRateBp + "bp)";
    }
}
