package com.openmarket.fulfillment.command.domain;

import java.time.Instant;
import java.util.UUID;

/** 배송 시뮬레이터가 배송 중으로 바꿨다(docs/architecture/context-map.md "fulfillment.shipment"). */
public record ShipmentInTransit(
        UUID shipmentId, UUID orderId, UUID sellerOrderId, Instant inTransitAt) {

    public static final String TYPE = "ShipmentInTransit";
}
