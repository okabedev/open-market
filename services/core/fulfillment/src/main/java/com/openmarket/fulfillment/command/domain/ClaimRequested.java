package com.openmarket.fulfillment.command.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 클레임을 신청했다(docs/architecture/context-map.md "fulfillment.claim"). order가 하위 주문 클레임을 바꾸고,
 * notification(판매자)과 file(첨부 확정)이 받는다.
 *
 * @param type 클레임 유형({@link ClaimType})
 * @param reason 사유({@link ClaimReason})
 * @param fault 귀책({@link Fault})
 * @param fileIds 첨부 사진. 발송 전 취소는 빈 목록이다
 */
public record ClaimRequested(
        UUID claimId,
        String type,
        UUID orderId,
        UUID sellerOrderId,
        UUID sellerId,
        UUID buyerId,
        String reason,
        String fault,
        List<UUID> fileIds,
        Instant requestedAt) {

    public static final String TYPE = "ClaimRequested";

    public static ClaimRequested of(Claim claim) {
        return new ClaimRequested(
                claim.id(),
                claim.type().name(),
                claim.orderId(),
                claim.sellerOrderId(),
                claim.sellerId(),
                claim.buyerId(),
                claim.reason().name(),
                claim.fault().name(),
                List.of(),
                claim.requestedAt());
    }
}
