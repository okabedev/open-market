package com.openmarket.fulfillment.command.domain;

import java.time.Instant;
import java.util.UUID;

/** 발주를 확인했다(docs/architecture/context-map.md "fulfillment.shipment"). order가 하위 주문 진행 상태를 바꾼다. */
public record ShipmentAccepted(
        UUID shipmentId, UUID orderId, UUID sellerOrderId, Instant acceptedAt) {

    public static final String TYPE = "ShipmentAccepted";
}
