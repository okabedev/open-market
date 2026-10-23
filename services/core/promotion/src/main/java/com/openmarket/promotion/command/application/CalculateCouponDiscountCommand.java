package com.openmarket.promotion.command.application;

import java.util.List;
import java.util.UUID;

/**
 * 주문의 쿠폰 할인 계산(docs/architecture/promotion.md "할인 계산").
 *
 * @param issuedCouponIds 쓸 발급 쿠폰(플랫폼 0~1장, 판매자마다 0~1장)
 * @param sellerOrders 하위 주문. 판매자는 겹치지 않는다
 */
public record CalculateCouponDiscountCommand(
        UUID buyerId, List<UUID> issuedCouponIds, List<SellerOrder> sellerOrders) {

    /**
     * 하위 주문 하나.
     *
     * @param amount 상품 금액(항목 금액의 합, 원)
     */
    public record SellerOrder(UUID sellerId, long amount) {}
}
