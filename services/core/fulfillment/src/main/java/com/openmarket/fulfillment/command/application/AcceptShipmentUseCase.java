package com.openmarket.fulfillment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentAccepted;
import com.openmarket.fulfillment.command.domain.ShipmentNotFoundException;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자가 하위 주문의 마지막 배송을 발주 확인한다(docs/architecture/fulfillment.md "발주 확인과 발송"). 처음 바꿀 때만 {@code
 * ShipmentAccepted}를 낸다.
 */
@Service
public class AcceptShipmentUseCase {

    static final String TOPIC = "fulfillment.shipment";

    private final ShipmentRepository shipments;
    private final Outbox outbox;
    private final Clock clock;

    public AcceptShipmentUseCase(ShipmentRepository shipments, Outbox outbox, Clock clock) {
        this.shipments = shipments;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(AcceptShipmentCommand command) {
        Shipment shipment =
                shipments
                        .findLatestForUpdateBySellerOrderId(command.sellerOrderId())
                        .filter(found -> found.isHandledBy(command.sellerId()))
                        .orElseThrow(ShipmentNotFoundException::new);
        Instant now = clock.instant();
        if (shipment.accept(now) == Shipment.Transition.UNCHANGED) {
            return;
        }
        shipments.save(shipment);
        outbox.append(
                TOPIC,
                shipment.sellerOrderId().toString(),
                ShipmentAccepted.TYPE,
                new ShipmentAccepted(
                        shipment.id(),
                        shipment.orderId(),
                        shipment.sellerOrderId(),
                        Objects.requireNonNull(shipment.acceptedAt())));
    }
}
