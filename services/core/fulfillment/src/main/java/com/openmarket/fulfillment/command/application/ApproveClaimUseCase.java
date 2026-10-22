package com.openmarket.fulfillment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.fulfillment.command.domain.Claim;
import com.openmarket.fulfillment.command.domain.ClaimApproved;
import com.openmarket.fulfillment.command.domain.ClaimNotFoundException;
import com.openmarket.fulfillment.command.domain.ClaimRepository;
import com.openmarket.fulfillment.command.domain.ClaimType;
import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자의 반품·교환 승인(CLM-3, docs/architecture/fulfillment.md "판매자 승인과 거부"). 클레임 행을 잠그고 승인해 {@code
 * ClaimApproved}를 낸다. 반품은 payment가 환불하고, 교환은 새 배송을 만든다. 이미 승인했으면 아무것도 하지 않는다.
 */
@Service
public class ApproveClaimUseCase {

    private final ClaimRepository claims;
    private final ShipmentRepository shipments;
    private final Outbox outbox;
    private final Clock clock;
    private final long returnShippingFee;

    public ApproveClaimUseCase(
            ClaimRepository claims,
            ShipmentRepository shipments,
            Outbox outbox,
            Clock clock,
            @Value("${openmarket.fulfillment.return-shipping-fee:6000}") long returnShippingFee) {
        this.claims = claims;
        this.shipments = shipments;
        this.outbox = outbox;
        this.clock = clock;
        this.returnShippingFee = returnShippingFee;
    }

    @Transactional
    public void execute(ApproveClaimCommand command) {
        Claim claim = findSellerClaim(claims, command.sellerId(), command.claimId());
        Shipment target =
                shipments
                        .findById(claim.shipmentId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "클레임의 배송이 없다: claimId=" + claim.id()));
        Instant now = clock.instant();
        if (claim.approve(target, command.restock(), returnShippingFee, now)
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
    }

    /**
     * 판매자 본인의 반품·교환 클레임을 잠그고 읽는다. 없거나 다른 판매자의 것이거나 발송 전 취소면 404 {@code
     * FULFILLMENT_CLAIM_NOT_FOUND}. 거부도 쓴다.
     */
    static Claim findSellerClaim(ClaimRepository claims, UUID sellerId, UUID claimId) {
        return claims.findForUpdateById(claimId)
                .filter(found -> found.isHandledBy(sellerId))
                .filter(found -> found.type() != ClaimType.CANCEL)
                .orElseThrow(ClaimNotFoundException::new);
    }
}
