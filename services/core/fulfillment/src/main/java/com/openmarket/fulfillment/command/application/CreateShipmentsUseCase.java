package com.openmarket.fulfillment.command.application;

import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentLine;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제된 주문의 하위 주문마다 {@code READY} 배송을 만든다(docs/architecture/fulfillment.md "배송"). 하위 주문의 첫 배송이 이미 있으면
 * 그 하위 주문은 넘긴다. 이벤트는 내지 않는다.
 */
@Service
public class CreateShipmentsUseCase {

    private final ShipmentRepository shipments;
    private final Clock clock;

    public CreateShipmentsUseCase(ShipmentRepository shipments, Clock clock) {
        this.shipments = shipments;
        this.clock = clock;
    }

    @Transactional
    public void execute(CreateShipmentsCommand command) {
        Instant now = clock.instant();
        for (CreateShipmentsCommand.SellerOrder sellerOrder : command.sellerOrders()) {
            if (shipments.existsFirstBySellerOrderId(sellerOrder.sellerOrderId())) {
                continue;
            }
            shipments.save(
                    Shipment.ready(
                            command.orderId(),
                            sellerOrder.sellerOrderId(),
                            sellerOrder.sellerId(),
                            command.buyerId(),
                            sellerOrder.amount(),
                            sellerOrder.lines().stream()
                                    .map(
                                            line ->
                                                    new ShipmentLine(
                                                            line.orderLineId(),
                                                            line.skuId(),
                                                            line.productId(),
                                                            line.productName(),
                                                            line.optionValues(),
                                                            line.categoryId(),
                                                            line.unitPrice(),
                                                            line.quantity(),
                                                            line.amount()))
                                    .toList(),
                            command.paidAt(),
                            now));
        }
    }
}
