package com.openmarket.settlement.query.dto;

/**
 * 판매자의 아직 확정되지 않은 정산 대상 합계(정산 예정 금액, STL-2). 대상이 없으면 모두 0이다.
 *
 * @param settlementAmount 정산 예정 금액
 */
public record PendingSettlementSummaryView(
        long itemCount,
        long salesAmount,
        long commissionAmount,
        long sellerCouponDiscount,
        long settlementAmount) {}
