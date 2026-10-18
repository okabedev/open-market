package com.openmarket.member.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.member.command.domain.AlreadySellerException;
import com.openmarket.member.command.domain.Seller;
import com.openmarket.member.command.domain.SellerApplication;
import com.openmarket.member.command.domain.SellerApplicationNotFoundException;
import com.openmarket.member.command.domain.SellerApplicationRepository;
import com.openmarket.member.command.domain.SellerApproved;
import com.openmarket.member.command.domain.SellerRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 입점 신청을 승인한다(SEL-2). 판매자를 만들고 {@code SellerApproved}를 쌓고 감사 로그를 남긴다. 이미 승인한 신청이면 아무것도 하지 않는다. */
@Service
public class ApproveSellerApplicationUseCase {

    static final String TOPIC = "member.seller";

    private final SellerApplicationRepository applications;
    private final SellerRepository sellers;
    private final Outbox outbox;
    private final AuditLog auditLog;
    private final Clock clock;

    public ApproveSellerApplicationUseCase(
            SellerApplicationRepository applications,
            SellerRepository sellers,
            Outbox outbox,
            AuditLog auditLog,
            Clock clock) {
        this.applications = applications;
        this.sellers = sellers;
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
        Instant now = clock.instant();
        if (!application.approve(command.adminId(), now)) {
            return;
        }
        if (sellers.existsById(application.memberId())) {
            throw new AlreadySellerException();
        }
        Seller seller = sellers.save(Seller.open(application, now));
        outbox.append(
                TOPIC,
                seller.id().toString(),
                SellerApproved.TYPE,
                new SellerApproved(seller.id(), seller.business().storeName()));
        auditLog.record(
                new AuditEntry(
                        command.adminId().toString(),
                        "SELLER_APPLICATION_APPROVED",
                        "SELLER_APPLICATION",
                        application.id().toString(),
                        null));
    }
}
