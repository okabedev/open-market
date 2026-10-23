package com.openmarket.promotion.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 쿠폰 정책 한 건. 규칙은 docs/architecture/promotion.md "쿠폰 정책".
 *
 * @param sellerId 판매자 쿠폰만
 * @param discountValue 정액은 원, 정률은 %
 * @param maxDiscount 정률의 최대 할인(원). 없으면 상한이 없다
 * @param issueEndAt 이 시각부터 내려받을 수 없다
 * @param validDays 발급 쿠폰의 사용 기한(발급 후 일수)
 * @param stoppedAt 발급을 중지한 시각
 * @param issuedCount 지금까지 발급한 수(DB 기준). 남은 수량은 quantity - issuedCount
 */
public record CouponView(
        UUID couponId,
        CouponIssuer issuer,
        @Nullable UUID sellerId,
        String name,
        DiscountType discountType,
        long discountValue,
        @Nullable Long maxDiscount,
        long minOrderAmount,
        int quantity,
        Instant issueStartAt,
        Instant issueEndAt,
        int validDays,
        CouponStatus status,
        @Nullable Instant stoppedAt,
        Instant createdAt,
        int issuedCount) {}
