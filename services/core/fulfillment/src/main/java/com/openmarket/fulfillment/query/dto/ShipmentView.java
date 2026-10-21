package com.openmarket.fulfillment.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 배송 하나. 송장과 단계별 시각은 그 단계를 지났을 때만 있다.
 *
 * @param carrier 택배사. 발송했을 때만
 * @param trackingNumber 송장 번호. 발송했을 때만
 */
public record ShipmentView(
        UUID shipmentId,
        ShipmentStatus status,
        @Nullable Carrier carrier,
        @Nullable String trackingNumber,
        Instant createdAt,
        @Nullable Instant acceptedAt,
        @Nullable Instant shippedAt,
        @Nullable Instant inTransitAt,
        @Nullable Instant deliveredAt) {}
