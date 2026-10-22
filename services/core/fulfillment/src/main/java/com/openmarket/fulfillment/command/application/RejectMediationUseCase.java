package com.openmarket.fulfillment.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.fulfillment.command.domain.Claim;
import com.openmarket.fulfillment.command.domain.ClaimRejected;
import com.openmarket.fulfillment.command.domain.ClaimRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자의 중재 거부 확정(ADM-2, docs/architecture/fulfillment.md "중재"). {@code
 * ClaimRejected}(mediated=true)를 내고 감사 로그를 남긴다. 이미 확정했으면 아무것도 하지 않는다.
 */
@Service
public class RejectMediationUseCase {

    private final ClaimRepository claims;
    private final Outbox outbox;
    private final AuditLog auditLog;
    private final Clock clock;

    public RejectMediationUseCase(
            ClaimRepository claims, Outbox outbox, AuditLog auditLog, Clock clock) {
        this.claims = claims;
        this.outbox = outbox;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public void execute(RejectMediationCommand command) {
        Claim claim = ApproveMediationUseCase.findMediatedClaim(claims, command.claimId());
        if (!claim.rejectByMediation(command.reason(), clock.instant())) {
            return;
        }
        claims.save(claim);
        ClaimRejected event = ClaimRejected.byMediation(claim);
        outbox.append(
                RequestCancelClaimUseCase.TOPIC,
                claim.sellerOrderId().toString(),
                ClaimRejected.TYPE,
                event);
        auditLog.record(
                new AuditEntry(
                        command.adminId().toString(),
                        "CLAIM_MEDIATION_REJECTED",
                        "CLAIM",
                        claim.id().toString(),
                        event.reason()));
    }
}
