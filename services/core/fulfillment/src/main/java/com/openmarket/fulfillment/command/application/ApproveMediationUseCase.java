package com.openmarket.fulfillment.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.fulfillment.command.domain.Claim;
import com.openmarket.fulfillment.command.domain.ClaimApproved;
import com.openmarket.fulfillment.command.domain.ClaimNotFoundException;
import com.openmarket.fulfillment.command.domain.ClaimRepository;
import com.openmarket.fulfillment.command.domain.ClaimType;
import com.openmarket.fulfillment.command.domain.Fault;
import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자의 중재 승인(ADM-2, docs/architecture/fulfillment.md "중재"). 관리자가 고른 귀책으로 승인하고 {@code
 * ClaimApproved}를 낸다. 반품은 재입고하지 않고, 교환은 새 배송을 만든다. 감사 로그를 남긴다. 이미 승인했으면 아무것도 하지 않는다.
 */
@Service
public class ApproveMediationUseCase {

    private final ClaimRepository claims;
    private final ShipmentRepository shipments;
    private final Outbox outbox;
    private final AuditLog auditLog;
    private final Clock clock;
    private final long returnShippingFee;

    public ApproveMediationUseCase(
            ClaimRepository claims,
            ShipmentRepository shipments,
            Outbox outbox,
            AuditLog auditLog,
            Clock clock,
            @Value("${openmarket.fulfillment.return-shipping-fee:6000}") long returnShippingFee) {
        this.claims = claims;
        this.shipments = shipments;
        this.outbox = outbox;
        this.auditLog = auditLog;
        this.clock = clock;
        this.returnShippingFee = returnShippingFee;
    }

    @Transactional
    public void execute(ApproveMediationCommand command) {
        Claim claim = findMediatedClaim(claims, command.claimId());
        Shipment target =
                shipments
                        .findById(claim.shipmentId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "클레임의 배송이 없다: claimId=" + claim.id()));
        Instant now = clock.instant();
        Fault fault = Fault.valueOf(command.fault().name());
        if (claim.approveByMediation(target, fault, returnShippingFee, now)
                == Claim.Approval.UNCHANGED) {
            return;
        }
        if (claim.type() == ClaimType.EXCHANGE) {
            shipments.save(claim.reship(target, now));
        }
        claims.save(claim);
        outbox.append(
                RequestCancelClaimUseCase.TOPIC,
                claim.sellerOrderId().toString(),
                ClaimApproved.TYPE,
                ClaimApproved.of(claim));
        auditLog.record(
                new AuditEntry(
                        command.adminId().toString(),
                        "CLAIM_MEDIATION_APPROVED",
                        "CLAIM",
                        claim.id().toString(),
                        "fault=" + fault.name()));
    }

    /**
     * 관리자가 결정할 반품·교환 클레임을 잠그고 읽는다. 없거나 발송 전 취소면 404 {@code FULFILLMENT_CLAIM_NOT_FOUND}. 거부 확정도 쓴다.
     */
    static Claim findMediatedClaim(ClaimRepository claims, UUID claimId) {
        return claims.findForUpdateById(claimId)
                .filter(found -> found.type() != ClaimType.CANCEL)
                .orElseThrow(ClaimNotFoundException::new);
    }
}
