package com.openmarket.promotion.ui.web;

import com.openmarket.promotion.command.application.CreateCouponCommand;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 쿠폰 정책 생성. 규칙은 docs/architecture/promotion.md "쿠폰 정책". 만든 뒤 바꿀 수 없다.
 *
 * @param name 1~50자
 * @param discountValue 정액은 1원 이상, 정률은 1~100(%)
 * @param maxDiscount 정률만. 1원 이상. 없으면 상한이 없다
 * @param minOrderAmount 0원 이상
 * @param quantity 발급 수량 1~1,000,000
 * @param issueEndAt 시작보다 뒤이고 지금보다 뒤. 이 시각부터 내려받을 수 없다
 * @param validDays 발급 후 사용 기한 1~365일
 */
public record CreateCouponRequest(
        @NotNull String name,
        @NotNull CreateCouponCommand.DiscountType discountType,
        long discountValue,
        @Nullable Long maxDiscount,
        long minOrderAmount,
        int quantity,
        @NotNull Instant issueStartAt,
        @NotNull Instant issueEndAt,
        int validDays) {

    CreateCouponCommand toCommand(UUID actorId, @Nullable UUID sellerId) {
        return new CreateCouponCommand(
                actorId,
                sellerId,
                name,
                discountType,
                discountValue,
                maxDiscount,
                minOrderAmount,
                quantity,
                issueStartAt,
                issueEndAt,
                validDays);
    }
}
