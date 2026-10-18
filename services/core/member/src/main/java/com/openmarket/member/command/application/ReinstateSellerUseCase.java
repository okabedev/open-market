package com.openmarket.member.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.member.command.domain.Seller;
import com.openmarket.member.command.domain.SellerNotFoundException;
import com.openmarket.member.command.domain.SellerReinstated;
import com.openmarket.member.command.domain.SellerRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자 정지를 푼다. 정지 상태가 아니면 아무것도 하지 않는다. */
@Service
public class ReinstateSellerUseCase {

    private final SellerRepository sellers;
    private final Outbox outbox;
    private final AuditLog auditLog;
    private final Clock clock;

    public ReinstateSellerUseCase(
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
        if (!seller.reinstate(clock.instant())) {
            return;
        }
        outbox.append(
                ApproveSellerApplicationUseCase.TOPIC,
                seller.id().toString(),
                SellerReinstated.TYPE,
                new SellerReinstated(seller.id()));
        auditLog.record(
                new AuditEntry(
                        command.adminId().toString(),
                        "SELLER_REINSTATED",
                        "SELLER",
                        seller.id().toString(),
                        null));
    }
}
