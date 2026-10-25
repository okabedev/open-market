package com.openmarket.settlement.query.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 정산 대상(구매확정된 하위 주문) 하나. 정산 내역과 정산 예정 내역에 쓴다.
 *
 * @param settlementAmount 판매 금액 − 수수료 − 판매자 쿠폰 부담분. 0 이하일 수 있다
 */
public record SettlementItemView(
        UUID sellerOrderId,
        UUID orderId,
        Instant confirmedAt,
        Instant periodStart,
        Instant periodEnd,
        long salesAmount,
        long commissionAmount,
        long sellerCouponDiscount,
        long settlementAmount,
        List<SettlementItemLineView> lines) {}
