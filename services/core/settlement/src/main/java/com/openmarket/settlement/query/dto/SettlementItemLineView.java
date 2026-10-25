package com.openmarket.settlement.query.dto;

import java.util.UUID;

/**
 * 정산 대상의 주문 항목 하나.
 *
 * @param amount 개당 가격 × 수량. 쿠폰을 빼기 전이다
 * @param commissionRateBp 받은 때 고정한 수수료율(bp)
 */
public record SettlementItemLineView(
        UUID orderLineId,
        UUID productId,
        String productName,
        UUID categoryId,
        int quantity,
        long amount,
        int commissionRateBp,
        long commissionAmount) {}
