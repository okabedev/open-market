package com.openmarket.promotion.command.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 쿠폰 정책의 조건. 만든 뒤 바꾸지 않는다. 규칙은 {@link Coupon}이 만들 때 검사한다(docs/architecture/promotion.md "쿠폰 정책").
 *
 * @param discountValue 정액은 원, 정률은 %
 * @param maxDiscount 정률의 최대 할인(원). 없으면 상한이 없다
 * @param minOrderAmount 적용 대상 금액이 이보다 작으면 쓸 수 없다(원)
 * @param quantity 발급 수량
 * @param issueEndAt 이 시각부터 내려받을 수 없다
 * @param validDays 발급 쿠폰의 사용 기한(발급 후 일수)
 */
public record CouponTerms(
        String name,
        DiscountType discountType,
        long discountValue,
        @Nullable Long maxDiscount,
        long minOrderAmount,
        int quantity,
        Instant issueStartAt,
        Instant issueEndAt,
        int validDays) {}
