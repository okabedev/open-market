package com.openmarket.fulfillment.command.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 구매자가 거부된 클레임의 중재를 요청했다(docs/architecture/context-map.md "fulfillment.claim"). order와
 * notification(관리자)이 받는다.
 */
public record ClaimMediationRequested(
        UUID claimId,
        String type,
        UUID orderId,
        UUID sellerOrderId,
        UUID sellerId,
        UUID buyerId,
        Instant requestedAt) {

    public static final String TYPE = "ClaimMediationRequested";

    public static ClaimMediationRequested of(Claim claim) {
        return new ClaimMediationRequested(
                claim.id(),
                claim.type().name(),
                claim.orderId(),
                claim.sellerOrderId(),
                claim.sellerId(),
                claim.buyerId(),
                Objects.requireNonNull(claim.mediationRequestedAt()));
    }
}
