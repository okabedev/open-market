package com.openmarket.settlement.command.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 판매자·기간의 정산을 확정했다(docs/architecture/context-map.md "settlement.settlement").
 *
 * @param commission 수수료 합
 * @param amount 정산 금액. 0 이하일 수 있다
 */
public record SettlementConfirmed(
        UUID settlementId,
        UUID sellerId,
        Instant periodStart,
        Instant periodEnd,
        long salesAmount,
        long commission,
        long sellerCouponDiscount,
        long amount,
        Instant confirmedAt) {

    public static final String TYPE = "SettlementConfirmed";

    public static SettlementConfirmed of(Settlement settlement) {
        return new SettlementConfirmed(
                settlement.id(),
                settlement.sellerId(),
                settlement.periodStart(),
                settlement.periodEnd(),
                settlement.salesAmount(),
                settlement.commissionAmount(),
                settlement.sellerCouponDiscount(),
                settlement.amount(),
                settlement.confirmedAt());
    }
}
