package com.openmarket.fulfillment.command.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 배송 시뮬레이터가 배송 완료로 바꿨다(docs/architecture/context-map.md "fulfillment.shipment"). 자동 구매확정 기한이 여기서
 * 시작한다.
 */
public record ShipmentDelivered(
        UUID shipmentId, UUID orderId, UUID sellerOrderId, UUID buyerId, Instant deliveredAt) {

    public static final String TYPE = "ShipmentDelivered";
}
