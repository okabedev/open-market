package com.openmarket.fulfillment.command.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 발송했다(docs/architecture/context-map.md "fulfillment.shipment"). order가 진행 상태를 바꾸고 notification이
 * 구매자에게 알린다.
 *
 * @param carrier 택배사 코드({@link Carrier})
 */
public record ShipmentShipped(
        UUID shipmentId,
        UUID orderId,
        UUID sellerOrderId,
        UUID sellerId,
        UUID buyerId,
        String carrier,
        String trackingNumber,
        Instant shippedAt) {

    public static final String TYPE = "ShipmentShipped";
}
