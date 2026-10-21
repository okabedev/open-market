package com.openmarket.fulfillment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentDelivered;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송 시뮬레이터: 배송 중인 배송을 배송 완료로 바꾸고 {@code ShipmentDelivered}를 낸다(docs/architecture/fulfillment.md "배송
 * 시뮬레이터"). 그사이 상태가 바뀌었으면 넘긴다.
 */
@Service
public class DeliverShipmentUseCase {

    private final ShipmentRepository shipments;
    private final Outbox outbox;
    private final Clock clock;

    public DeliverShipmentUseCase(ShipmentRepository shipments, Outbox outbox, Clock clock) {
        this.shipments = shipments;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(UUID shipmentId) {
        Optional<Shipment> found = shipments.findForUpdateById(shipmentId);
        if (found.isEmpty() || !found.get().deliver(clock.instant())) {
            return;
        }
        Shipment shipment = found.get();
        shipments.save(shipment);
        outbox.append(
                AcceptShipmentUseCase.TOPIC,
                shipment.sellerOrderId().toString(),
                ShipmentDelivered.TYPE,
                new ShipmentDelivered(
                        shipment.id(),
                        shipment.orderId(),
                        shipment.sellerOrderId(),
                        shipment.buyerId(),
                        Objects.requireNonNull(shipment.deliveredAt())));
    }
}
