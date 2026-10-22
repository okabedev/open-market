package com.openmarket.fulfillment.command.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 클레임을 승인했다(docs/architecture/context-map.md "fulfillment.claim"). payment가 환불하고(교환 제외), order와
 * notification이 받는다.
 *
 * @param refundAmount 환불 금액(원). 교환은 0
 */
public record ClaimApproved(
        UUID claimId,
        String type,
        UUID orderId,
        UUID sellerOrderId,
        UUID sellerId,
        UUID buyerId,
        String fault,
        long refundAmount,
        Instant approvedAt) {

    public static final String TYPE = "ClaimApproved";

    public static ClaimApproved of(Claim claim) {
        return new ClaimApproved(
                claim.id(),
                claim.type().name(),
                claim.orderId(),
                claim.sellerOrderId(),
                claim.sellerId(),
                claim.buyerId(),
                claim.fault().name(),
                claim.refundAmount(),
                Objects.requireNonNull(claim.approvedAt()));
    }
}
