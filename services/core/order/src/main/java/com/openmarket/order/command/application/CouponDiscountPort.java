package com.openmarket.order.command.application;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * promotion의 쿠폰 할인 계산(docs/architecture/promotion.md "할인 계산 API(내부)"). 상태를 바꾸지 않는 조회다. promotion을
 * 부를 수 없으면 503 {@code ORDER_PROMOTION_UNAVAILABLE}을 던진다.
 */
public interface CouponDiscountPort {

    /**
     * 주문의 쿠폰 할인을 계산한다.
     *
     * @param sellerOrders 하위 주문 순서대로 판매자와 상품 금액
     */
    CouponDiscount calculate(
            UUID buyerId, List<UUID> issuedCouponIds, List<SellerAmount> sellerOrders);

    /** 하위 주문 하나의 판매자와 상품 금액(원). */
    record SellerAmount(UUID sellerId, long amount) {}

    /**
     * 계산 결과.
     *
     * @param applicable 모든 쿠폰을 쓸 수 있는가. 아니면 {@code unavailable}에 이유가 있다
     * @param sellerOrders 요청한 하위 주문 순서
     * @param paymentAmount 주문 결제 금액(원)
     */
    record CouponDiscount(
            boolean applicable,
            List<SellerOrderCoupon> sellerOrders,
            @Nullable UUID platformIssuedCouponId,
            long platformCouponDiscount,
            long paymentAmount,
            List<Unavailable> unavailable) {}

    /**
     * 하위 주문 하나의 할인.
     *
     * @param amount 상품 금액(원)
     * @param platformCouponDiscount 플랫폼 쿠폰 할인 중 이 하위 주문의 몫(원)
     */
    record SellerOrderCoupon(
            UUID sellerId,
            long amount,
            @Nullable UUID sellerIssuedCouponId,
            long sellerCouponDiscount,
            long platformCouponDiscount,
            long paymentAmount) {}

    /**
     * 쓸 수 없는 쿠폰.
     *
     * @param reason promotion의 이유 코드(예: {@code EXPIRED})
     */
    record Unavailable(UUID issuedCouponId, String reason) {}
}
