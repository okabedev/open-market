package com.openmarket.fulfillment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.fulfillment.command.domain.ClaimRepository;
import com.openmarket.fulfillment.command.domain.ConfirmMode;
import com.openmarket.fulfillment.command.domain.PurchaseConfirmation;
import com.openmarket.fulfillment.command.domain.PurchaseConfirmationRepository;
import com.openmarket.fulfillment.command.domain.PurchaseConfirmed;
import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import com.openmarket.fulfillment.command.domain.ShipmentStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자동 구매확정: 확정 기한이 지난 배송 완료 배송을 {@code AUTO}로 확정하고 {@code PurchaseConfirmed}를 낸다
 * (docs/architecture/fulfillment.md "구매확정"). 배송 행을 잠그고 다시 확인해, 그사이 구매자가 확정했거나 클레임을 신청했거나 상태·기한이 맞지
 * 않으면 넘긴다.
 */
@Service
public class AutoConfirmPurchaseUseCase {

    private final ShipmentRepository shipments;
    private final PurchaseConfirmationRepository confirmations;
    private final ClaimRepository claims;
    private final Outbox outbox;
    private final Clock clock;

    public AutoConfirmPurchaseUseCase(
            ShipmentRepository shipments,
            PurchaseConfirmationRepository confirmations,
            ClaimRepository claims,
            Outbox outbox,
            Clock clock) {
        this.shipments = shipments;
        this.confirmations = confirmations;
        this.claims = claims;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(UUID shipmentId) {
        Optional<Shipment> found = shipments.findForUpdateById(shipmentId);
        if (found.isEmpty()) {
            return;
        }
        Shipment shipment = found.get();
        // 교환 재발송이 있으면 처음 배송의 기한은 쓰지 않는다. 마지막 배송만 확정한다.
        boolean latest =
                shipments
                        .findLatestForUpdateBySellerOrderId(shipment.sellerOrderId())
                        .map(last -> last.id().equals(shipment.id()))
                        .orElse(false);
        if (!latest) {
            return;
        }
        Instant now = clock.instant();
        Instant due = shipment.confirmDueAt();
        if (shipment.status() != ShipmentStatus.DELIVERED
                || due == null
                || due.isAfter(now)
                || confirmations.existsBySellerOrderId(shipment.sellerOrderId())
                || claims.existsOngoingBySellerOrderId(shipment.sellerOrderId())) {
            return;
        }
        PurchaseConfirmation confirmation =
                confirmations.save(PurchaseConfirmation.of(shipment, ConfirmMode.AUTO, now));
        outbox.append(
                AcceptShipmentUseCase.TOPIC,
                shipment.sellerOrderId().toString(),
                PurchaseConfirmed.TYPE,
                PurchaseConfirmed.of(shipment, confirmation));
    }
}
