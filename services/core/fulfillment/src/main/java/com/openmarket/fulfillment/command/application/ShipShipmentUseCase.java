package com.openmarket.fulfillment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.fulfillment.command.domain.Claim;
import com.openmarket.fulfillment.command.domain.ClaimCompleted;
import com.openmarket.fulfillment.command.domain.ClaimRepository;
import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentNotFoundException;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import com.openmarket.fulfillment.command.domain.ShipmentShipped;
import com.openmarket.fulfillment.command.domain.TrackingInfo;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자가 송장을 입력하고 하위 주문의 마지막 배송을 발송한다(docs/architecture/fulfillment.md "발주 확인과 발송"). 처음 바꿀 때만 {@code
 * ShipmentShipped}를 낸다. 교환 재발송 배송이면 같은 트랜잭션에서 교환 클레임을 끝내고 {@code ClaimCompleted}를 낸다("교환 재발송").
 */
@Service
public class ShipShipmentUseCase {

    private final ShipmentRepository shipments;
    private final ClaimRepository claims;
    private final Outbox outbox;
    private final Clock clock;

    public ShipShipmentUseCase(
            ShipmentRepository shipments, ClaimRepository claims, Outbox outbox, Clock clock) {
        this.shipments = shipments;
        this.claims = claims;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(ShipShipmentCommand command) {
        TrackingInfo tracking = TrackingInfo.of(command.carrier(), command.trackingNumber());
        Shipment shipment =
                shipments
                        .findLatestForUpdateBySellerOrderId(command.sellerOrderId())
                        .filter(found -> found.isHandledBy(command.sellerId()))
                        .orElseThrow(ShipmentNotFoundException::new);
        if (shipment.ship(tracking, clock.instant()) == Shipment.Transition.UNCHANGED) {
            return;
        }
        shipments.save(shipment);
        outbox.append(
                AcceptShipmentUseCase.TOPIC,
                shipment.sellerOrderId().toString(),
                ShipmentShipped.TYPE,
                new ShipmentShipped(
                        shipment.id(),
                        shipment.orderId(),
                        shipment.sellerOrderId(),
                        shipment.sellerId(),
                        shipment.buyerId(),
                        tracking.carrier().name(),
                        tracking.trackingNumber(),
                        Objects.requireNonNull(shipment.shippedAt())));
        UUID exchangeClaimId = shipment.claimId();
        if (exchangeClaimId != null) {
            completeExchange(exchangeClaimId, shipment);
        }
    }

    private void completeExchange(UUID claimId, Shipment reshipment) {
        Claim claim =
                claims.findForUpdateById(claimId)
                        .orElseThrow(
                                () -> new IllegalStateException("교환 클레임이 없다: claimId=" + claimId));
        if (!claim.complete(clock.instant())) {
            return;
        }
        claims.save(claim);
        outbox.append(
                RequestCancelClaimUseCase.TOPIC,
                claim.sellerOrderId().toString(),
                ClaimCompleted.TYPE,
                ClaimCompleted.of(claim, reshipment));
    }
}
