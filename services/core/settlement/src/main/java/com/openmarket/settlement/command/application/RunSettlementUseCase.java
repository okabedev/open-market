package com.openmarket.settlement.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.core.id.Ids;
import com.openmarket.settlement.command.application.ConfirmSettlementCommand.AdminRun;
import com.openmarket.settlement.command.domain.SellerPeriod;
import com.openmarket.settlement.command.domain.SettlementItemRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 정산 작업 한 번(docs/architecture/settlement.md "정산 확정"). 끝난 지 마감 지연이 지난 기간 중 확정되지 않은 대상이 있는 판매자·기간을
 * 오래된 순으로 확정한다. 판매자·기간마다 트랜잭션을 따로 연다({@link ConfirmSettlementUseCase}). 정기 작업과 관리자 즉시 실행이 같이 쓰고, 겹쳐
 * 돌아도 판매자 잠금과 판매자·기간 유니크로 결과가 같다.
 *
 * <p>관리자 즉시 실행은 확정한 정산마다 확정과 같은 트랜잭션에서 감사 로그를 남기고({@link ConfirmSettlementUseCase}), 끝나면 요약 {@value
 * #AUDIT_ACTION}(대상은 이번 실행 ID, 부가 설명은 확정 건수)를 남긴다(NFR-8). 도중에 실패해도 요약을 남긴다. 정기 실행은 사람이 한 작업이 아니라
 * 남기지 않는다.
 */
@Service
public class RunSettlementUseCase {

    static final int PAGE_SIZE = 500;
    static final String AUDIT_ACTION = "SETTLEMENT_RUN_EXECUTED";
    static final String AUDIT_TARGET = "SETTLEMENT_RUN";

    private final SettlementItemRepository items;
    private final ConfirmSettlementUseCase confirm;
    private final AuditLog auditLog;
    private final Clock clock;
    private final Duration closeDelay;

    public RunSettlementUseCase(
            SettlementItemRepository items,
            ConfirmSettlementUseCase confirm,
            AuditLog auditLog,
            Clock clock,
            @Value("${openmarket.settlement.job.close-delay:3h}") Duration closeDelay) {
        this.items = items;
        this.confirm = confirm;
        this.auditLog = auditLog;
        this.clock = clock;
        this.closeDelay = closeDelay;
    }

    /** 관리자 즉시 실행. 확정한 정산 수. */
    public int execute(RunSettlementCommand command) {
        AdminRun adminRun = new AdminRun(command.adminId(), Ids.newId());
        AtomicInteger confirmed = new AtomicInteger();
        String outcome = "(오류로 중단)";
        try {
            confirmAll(adminRun, confirmed);
            outcome = "";
            return confirmed.get();
        } finally {
            auditLog.record(
                    new AuditEntry(
                            adminRun.adminId().toString(),
                            AUDIT_ACTION,
                            AUDIT_TARGET,
                            adminRun.runId().toString(),
                            "확정 " + confirmed.get() + "건" + outcome));
        }
    }

    /** 정기 실행. 확정한 정산 수. */
    public int execute() {
        AtomicInteger confirmed = new AtomicInteger();
        confirmAll(null, confirmed);
        return confirmed.get();
    }

    private void confirmAll(@Nullable AdminRun adminRun, AtomicInteger confirmed) {
        Instant closedBy = clock.instant().minus(closeDelay);
        Set<SellerPeriod> tried = new HashSet<>();
        while (true) {
            List<SellerPeriod> page = items.findClosablePeriods(closedBy, PAGE_SIZE);
            List<SellerPeriod> fresh = page.stream().filter(tried::add).toList();
            if (fresh.isEmpty()) {
                return;
            }
            for (SellerPeriod period : fresh) {
                if (confirm.execute(
                        new ConfirmSettlementCommand(
                                period.sellerId(), period.periodStart(), adminRun))) {
                    confirmed.incrementAndGet();
                }
            }
        }
    }
}
