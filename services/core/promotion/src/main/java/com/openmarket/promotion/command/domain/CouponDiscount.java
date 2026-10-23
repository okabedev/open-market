package com.openmarket.promotion.command.domain;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 주문의 쿠폰 할인 계산 결과({@link DiscountCalculator}). 쓸 수 없는 쿠폰이 하나라도 있으면 {@link #applicable()}이 {@code
 * false}이고 주문을 만들지 않는다.
 *
 * @param sellerOrders 요청한 하위 주문 순서 그대로
 * @param platformIssuedCouponId 적용한 플랫폼 쿠폰
 * @param platformDiscount 플랫폼 쿠폰 할인 전체(하위 주문 몫의 합)
 * @param unavailable 쓸 수 없는 쿠폰과 이유. 요청 순서
 */
public record CouponDiscount(
        List<SellerOrderDiscount> sellerOrders,
        @Nullable UUID platformIssuedCouponId,
        long platformDiscount,
        List<Unavailable> unavailable) {

    public boolean applicable() {
        return unavailable.isEmpty();
    }

    /** 주문 결제 금액. 하위 주문 결제 금액의 합이다. */
    public long paymentAmount() {
        return sellerOrders.stream().mapToLong(SellerOrderDiscount::paymentAmount).sum();
    }

    /**
     * 할인을 계산할 하위 주문.
     *
     * @param amount 상품 금액(항목 금액의 합)
     */
    public record SellerOrderAmount(UUID sellerId, long amount) {}

    /**
     * 하위 주문 하나의 할인.
     *
     * @param amount 상품 금액
     * @param sellerIssuedCouponId 적용한 판매자 쿠폰
     * @param platformCouponDiscount 플랫폼 쿠폰 할인 중 이 하위 주문의 몫
     */
    public record SellerOrderDiscount(
            UUID sellerId,
            long amount,
            @Nullable UUID sellerIssuedCouponId,
            long sellerCouponDiscount,
            long platformCouponDiscount) {

        /** 결제 금액 = 상품 금액 − 판매자 쿠폰 할인 − 플랫폼 쿠폰 할인 몫. */
        public long paymentAmount() {
            return amount - sellerCouponDiscount - platformCouponDiscount;
        }
    }

    public record Unavailable(UUID issuedCouponId, CouponUnavailableReason reason) {}
}
