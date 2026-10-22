package com.openmarket.fulfillment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.fulfillment.command.domain.Claim;
import com.openmarket.fulfillment.command.domain.ClaimInProgressException;
import com.openmarket.fulfillment.command.domain.ClaimReason;
import com.openmarket.fulfillment.command.domain.ClaimRepository;
import com.openmarket.fulfillment.command.domain.ClaimRequested;
import com.openmarket.fulfillment.command.domain.ClaimType;
import com.openmarket.fulfillment.command.domain.PurchaseConfirmationRepository;
import com.openmarket.fulfillment.command.domain.PurchaseConfirmedException;
import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentNotFoundException;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 반품·교환 신청(CLM-2, docs/architecture/fulfillment.md "반품·교환 신청"). 하위 주문의 마지막 배송을 잠가 구매확정과 겹치지 않게 하고,
 * {@code REQUESTED} 클레임을 만들어 {@code ClaimRequested}를 낸다.
 */
@Service
public class RequestClaimUseCase {

    private final ShipmentRepository shipments;
    private final ClaimRepository claims;
    private final PurchaseConfirmationRepository confirmations;
    private final Outbox outbox;
    private final Clock clock;

    public RequestClaimUseCase(
            ShipmentRepository shipments,
            ClaimRepository claims,
            PurchaseConfirmationRepository confirmations,
            Outbox outbox,
            Clock clock) {
        this.shipments = shipments;
        this.claims = claims;
        this.confirmations = confirmations;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** 만든 클레임의 ID를 돌려준다. */
    @Transactional
    public UUID execute(RequestClaimCommand command) {
        Shipment shipment =
                shipments
                        .findLatestForUpdateBySellerOrderId(command.sellerOrderId())
                        .filter(found -> found.isReceivedBy(command.buyerId()))
                        .orElseThrow(ShipmentNotFoundException::new);
        if (confirmations.existsBySellerOrderId(shipment.sellerOrderId())) {
            throw new PurchaseConfirmedException();
        }
        if (claims.existsOngoingBySellerOrderId(shipment.sellerOrderId())) {
            throw new ClaimInProgressException();
        }
        Claim claim =
                claims.save(
                        Claim.request(
                                ClaimType.valueOf(command.type().name()),
                                shipment,
                                ClaimReason.valueOf(command.reason().name()),
                                command.description(),
                                command.fileIds(),
                                clock.instant()));
        outbox.append(
                RequestCancelClaimUseCase.TOPIC,
                claim.sellerOrderId().toString(),
                ClaimRequested.TYPE,
                ClaimRequested.of(claim));
        return claim.id();
    }
}
