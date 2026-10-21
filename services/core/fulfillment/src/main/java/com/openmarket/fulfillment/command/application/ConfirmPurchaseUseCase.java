package com.openmarket.fulfillment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.fulfillment.command.domain.ConfirmMode;
import com.openmarket.fulfillment.command.domain.PurchaseConfirmation;
import com.openmarket.fulfillment.command.domain.PurchaseConfirmationRepository;
import com.openmarket.fulfillment.command.domain.PurchaseConfirmed;
import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentNotFoundException;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매자가 하위 주문을 구매확정하고 {@code PurchaseConfirmed}를 낸다(docs/architecture/fulfillment.md "구매확정"). 하위 주문의
 * 마지막 배송을 잠가 자동 확정과 겹치지 않게 한다. 이미 확정됐으면 아무것도 하지 않는다.
 */
@Service
public class ConfirmPurchaseUseCase {

    private final ShipmentRepository shipments;
    private final PurchaseConfirmationRepository confirmations;
    private final Outbox outbox;
    private final Clock clock;

    public ConfirmPurchaseUseCase(
            ShipmentRepository shipments,
            PurchaseConfirmationRepository confirmations,
            Outbox outbox,
            Clock clock) {
        this.shipments = shipments;
        this.confirmations = confirmations;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(ConfirmPurchaseCommand command) {
        Shipment shipment =
                shipments
                        .findLatestForUpdateBySellerOrderId(command.sellerOrderId())
                        .filter(found -> found.isReceivedBy(command.buyerId()))
                        .orElseThrow(ShipmentNotFoundException::new);
        if (confirmations.existsBySellerOrderId(shipment.sellerOrderId())) {
            return;
        }
        PurchaseConfirmation confirmation =
                confirmations.save(
                        PurchaseConfirmation.of(shipment, ConfirmMode.BUYER, clock.instant()));
        outbox.append(
                AcceptShipmentUseCase.TOPIC,
                shipment.sellerOrderId().toString(),
                PurchaseConfirmed.TYPE,
                PurchaseConfirmed.of(shipment, confirmation));
    }
}
