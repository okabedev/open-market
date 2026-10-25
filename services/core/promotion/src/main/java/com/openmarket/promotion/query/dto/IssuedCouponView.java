package com.openmarket.promotion.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 내 쿠폰 한 장. 쿠폰 정책의 이름과 할인 조건을 함께 싣는다.
 *
 * @param sellerId 판매자 쿠폰만
 * @param discountValue 정액은 원, 정률은 %
 * @param maxDiscount 정률의 최대 할인(원). 없으면 상한이 없다
 * @param minOrderAmount 적용 대상 금액이 이보다 작으면 쓸 수 없다(원)
 * @param expiresAt 이 시각부터 쓸 수 없다
 */
public record IssuedCouponView(
        UUID issuedCouponId,
        UUID couponId,
        CouponIssuer issuer,
        @Nullable UUID sellerId,
        String name,
        DiscountType discountType,
        long discountValue,
        @Nullable Long maxDiscount,
        long minOrderAmount,
        IssuedCouponState state,
        Instant issuedAt,
        Instant expiresAt) {}
