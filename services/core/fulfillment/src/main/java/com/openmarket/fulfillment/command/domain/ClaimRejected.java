package com.openmarket.fulfillment.command.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 클레임을 거부했다(docs/architecture/context-map.md "fulfillment.claim"). order와 notification이 받는다.
 *
 * @param reason 거부 사유
 * @param mediated 중재 결과로 거부가 확정됐으면 true. 판매자 거부는 false
 */
public record ClaimRejected(
        UUID claimId,
        String type,
        UUID orderId,
        UUID sellerOrderId,
        UUID buyerId,
        String reason,
        boolean mediated,
        Instant rejectedAt) {

    public static final String TYPE = "ClaimRejected";

    /** 판매자 거부. */
    public static ClaimRejected bySeller(Claim claim) {
        return new ClaimRejected(
                claim.id(),
                claim.type().name(),
                claim.orderId(),
                claim.sellerOrderId(),
                claim.buyerId(),
                Objects.requireNonNull(claim.rejectionReason()),
                false,
                Objects.requireNonNull(claim.rejectedAt()));
    }
}
