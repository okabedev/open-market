package com.openmarket.promotion.command.application;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 쿠폰 정책 생성(PRM-1). 규칙은 docs/architecture/promotion.md "쿠폰 정책".
 *
 * @param actorId 만드는 사람(토큰의 {@code sub})
 * @param sellerId 판매자 쿠폰이면 판매자 ID. 없으면 관리자가 만드는 플랫폼 쿠폰이다
 */
public record CreateCouponCommand(
        UUID actorId,
        @Nullable UUID sellerId,
        String name,
        DiscountType discountType,
        long discountValue,
        @Nullable Long maxDiscount,
        long minOrderAmount,
        int quantity,
        Instant issueStartAt,
        Instant issueEndAt,
        int validDays) {

    public enum DiscountType {
        FIXED,
        RATE
    }
}
