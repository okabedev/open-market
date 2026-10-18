package com.openmarket.member.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.member.command.domain.SellerApplication;
import com.openmarket.member.command.domain.SellerApplicationNotFoundException;
import com.openmarket.member.command.domain.SellerApplicationRepository;
import com.openmarket.member.command.domain.SellerRejected;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 입점 신청을 반려한다(SEL-2). 사유가 꼭 있어야 한다. 이미 반려한 신청이면 아무것도 하지 않는다. */
@Service
public class RejectSellerApplicationUseCase {

    private final SellerApplicationRepository applications;
    private final Outbox outbox;
    private final AuditLog auditLog;
    private final Clock clock;

    public RejectSellerApplicationUseCase(
            SellerApplicationRepository applications,
            Outbox outbox,
            AuditLog auditLog,
            Clock clock) {
        this.applications = applications;
        this.outbox = outbox;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public void execute(ReviewSellerApplicationCommand command) {
        SellerApplication application =
                applications
                        .findForUpdateById(command.applicationId())
                        .orElseThrow(SellerApplicationNotFoundException::new);
        if (!application.reject(
                command.adminId(),
                Objects.requireNonNullElse(command.reason(), ""),
                clock.instant())) {
            return;
        }
        String reason = Objects.requireNonNull(application.rejectReason());
        outbox.append(
                ApproveSellerApplicationUseCase.TOPIC,
                application.memberId().toString(),
                SellerRejected.TYPE,
                new SellerRejected(application.memberId(), application.id(), reason));
        auditLog.record(
                new AuditEntry(
                        command.adminId().toString(),
                        "SELLER_APPLICATION_REJECTED",
                        "SELLER_APPLICATION",
                        application.id().toString(),
                        reason));
    }
}
