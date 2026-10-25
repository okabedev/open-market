package com.openmarket.settlement.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 확정된 정산 한 건.
 *
 * @param periodEnd 정산 기간의 끝. 이 시각은 기간에 들지 않는다({@code [periodStart, periodEnd)})
 * @param amount 정산 금액(판매 금액 − 수수료 − 판매자 쿠폰 부담분). 0 이하일 수 있다
 * @param paidOutAt 지급 처리 시각. 지급 전이면 {@code null}
 */
public record SettlementView(
        UUID settlementId,
        UUID sellerId,
        Instant periodStart,
        Instant periodEnd,
        int itemCount,
        long salesAmount,
        long commissionAmount,
        long sellerCouponDiscount,
        long amount,
        SettlementStatus status,
        Instant confirmedAt,
        @Nullable Instant paidOutAt) {}
