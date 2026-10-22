package com.openmarket.fulfillment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.fulfillment.command.domain.Claim;
import com.openmarket.fulfillment.command.domain.ClaimApproved;
import com.openmarket.fulfillment.command.domain.ClaimInProgressException;
import com.openmarket.fulfillment.command.domain.ClaimRepository;
import com.openmarket.fulfillment.command.domain.ClaimRequested;
import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentNotFoundException;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import com.openmarket.fulfillment.command.domain.ShipmentStatus;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발송 전 취소(CLM-1, docs/architecture/fulfillment.md "발송 전 취소"). 하위 주문의 마지막 배송을 잠가 판매자의 발송과 겹치지 않게 하고,
 * 클레임을 신청과 동시에 승인해 {@code ClaimRequested}, {@code ClaimApproved}를 함께 낸다. 이미 취소했으면 아무것도 하지 않는다.
 */
@Service
public class RequestCancelClaimUseCase {

    static final String TOPIC = "fulfillment.claim";

    private final ShipmentRepository shipments;
    private final ClaimRepository claims;
    private final Outbox outbox;
    private final Clock clock;

    public RequestCancelClaimUseCase(
            ShipmentRepository shipments, ClaimRepository claims, Outbox outbox, Clock clock) {
        this.shipments = shipments;
        this.claims = claims;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(RequestCancelClaimCommand command) {
        Shipment shipment =
                shipments
                        .findLatestForUpdateBySellerOrderId(command.sellerOrderId())
                        .filter(found -> found.isReceivedBy(command.buyerId()))
                        .orElseThrow(ShipmentNotFoundException::new);
        if (shipment.status() == ShipmentStatus.CANCELLED) {
            return;
        }
        if (claims.existsOngoingBySellerOrderId(shipment.sellerOrderId())) {
            throw new ClaimInProgressException();
        }
        Claim claim = claims.save(Claim.cancel(shipment, clock.instant()));
        shipments.save(shipment);
        String key = claim.sellerOrderId().toString();
        outbox.append(TOPIC, key, ClaimRequested.TYPE, ClaimRequested.of(claim));
        outbox.append(TOPIC, key, ClaimApproved.TYPE, ClaimApproved.of(claim));
    }
}
