package com.openmarket.member.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.member.command.domain.Seller;
import com.openmarket.member.command.domain.SellerNotFoundException;
import com.openmarket.member.command.domain.SellerRepository;
import com.openmarket.member.command.domain.SellerSuspended;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자를 정지한다(SEL-3). 사유가 꼭 있어야 한다. 이미 정지했으면 아무것도 하지 않는다. */
@Service
public class SuspendSellerUseCase {

    private final SellerRepository sellers;
    private final Outbox outbox;
    private final AuditLog auditLog;
    private final Clock clock;

    public SuspendSellerUseCase(
            SellerRepository sellers, Outbox outbox, AuditLog auditLog, Clock clock) {
        this.sellers = sellers;
        this.outbox = outbox;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public void execute(ChangeSellerStatusCommand command) {
        Seller seller =
                sellers.findForUpdateById(command.sellerId())
                        .orElseThrow(SellerNotFoundException::new);
        if (!seller.suspend(Objects.requireNonNullElse(command.reason(), ""), clock.instant())) {
            return;
        }
        String reason = Objects.requireNonNull(seller.suspendReason());
        outbox.append(
                ApproveSellerApplicationUseCase.TOPIC,
                seller.id().toString(),
                SellerSuspended.TYPE,
                new SellerSuspended(seller.id(), reason));
        auditLog.record(
                new AuditEntry(
                        command.adminId().toString(),
                        "SELLER_SUSPENDED",
                        "SELLER",
                        seller.id().toString(),
                        reason));
    }
}
