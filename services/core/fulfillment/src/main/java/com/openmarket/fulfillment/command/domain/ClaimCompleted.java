package com.openmarket.fulfillment.command.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 클레임이 끝났다(docs/architecture/context-map.md "fulfillment.claim"). catalog가 재입고하고, order와
 * notification이 받는다.
 *
 * @param refundAmount 환불 금액(원). 교환은 0
 * @param restockLines 재입고할 SKU와 수량. 재입고하지 않으면 빈 목록
 */
public record ClaimCompleted(
        UUID claimId,
        String type,
        UUID orderId,
        UUID sellerOrderId,
        UUID buyerId,
        long refundAmount,
        List<RestockLine> restockLines,
        Instant completedAt) {

    public static final String TYPE = "ClaimCompleted";

    /** 끝난 클레임과 대상 배송으로 만든다. 발송 전 취소는 배송 항목 전부를 SKU별로 합쳐 재입고한다. */
    public static ClaimCompleted of(Claim claim, Shipment shipment) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        if (claim.type() == ClaimType.CANCEL) {
            shipment.lines()
                    .forEach(line -> quantities.merge(line.skuId(), line.quantity(), Integer::sum));
        }
        return new ClaimCompleted(
                claim.id(),
                claim.type().name(),
                claim.orderId(),
                claim.sellerOrderId(),
                claim.buyerId(),
                claim.refundAmount(),
                quantities.entrySet().stream()
                        .map(entry -> new RestockLine(entry.getKey(), entry.getValue()))
                        .toList(),
                Objects.requireNonNull(claim.completedAt()));
    }

    public record RestockLine(UUID skuId, int quantity) {}
}
