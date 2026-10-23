package com.openmarket.promotion.ui.web;

import com.openmarket.promotion.command.application.CalculateCouponDiscountCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 주문의 쿠폰 할인 계산(order → promotion 내부 조회).
 *
 * @param issuedCouponIds 쓸 발급 쿠폰. 비어도 된다
 * @param sellerOrders 하위 주문. 판매자는 겹치지 않는다
 */
public record CalculateCouponDiscountRequest(
        @NotNull UUID buyerId,
        @NotNull List<UUID> issuedCouponIds,
        @NotNull @Valid List<SellerOrder> sellerOrders) {

    /**
     * 하위 주문 하나.
     *
     * @param amount 상품 금액(항목 금액의 합, 원)
     */
    public record SellerOrder(@NotNull UUID sellerId, long amount) {}

    CalculateCouponDiscountCommand toCommand() {
        return new CalculateCouponDiscountCommand(
                buyerId,
                issuedCouponIds,
                sellerOrders.stream()
                        .map(
                                o ->
                                        new CalculateCouponDiscountCommand.SellerOrder(
                                                o.sellerId(), o.amount()))
                        .toList());
    }
}
