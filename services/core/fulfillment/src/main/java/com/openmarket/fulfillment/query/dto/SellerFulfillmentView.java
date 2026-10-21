package com.openmarket.fulfillment.query.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 판매자 하위 주문의 배송 진행(docs/architecture/fulfillment.md "API"). 항목과 배송지는 order의 판매자 하위 주문 상세에 있다.
 *
 * @param status 마지막 배송의 상태
 * @param shipments 배송 목록. 만든 순서이고 교환 재발송이 있으면 둘 이상이다
 */
public record SellerFulfillmentView(
        UUID sellerOrderId, UUID orderId, ShipmentStatus status, List<Shipment> shipments) {

    /**
     * 배송 하나. 송장과 단계별 시각은 그 단계를 지났을 때만 있다.
     *
     * @param carrier 택배사. 발송했을 때만
     * @param trackingNumber 송장 번호. 발송했을 때만
     */
    @Schema(name = "SellerFulfillmentShipmentView")
    public record Shipment(
            UUID shipmentId,
            ShipmentStatus status,
            @Nullable Carrier carrier,
            @Nullable String trackingNumber,
            Instant createdAt,
            @Nullable Instant acceptedAt,
            @Nullable Instant shippedAt,
            @Nullable Instant inTransitAt,
            @Nullable Instant deliveredAt) {}
}
