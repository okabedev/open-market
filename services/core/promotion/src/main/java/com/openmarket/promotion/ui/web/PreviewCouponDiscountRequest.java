package com.openmarket.promotion.ui.web;

import com.openmarket.promotion.command.application.CalculateCouponDiscountCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 주문서 할인 미리보기. 구매자는 토큰에서 정하므로 받지 않는다.
 *
 * @param issuedCouponIds 쓸 발급 쿠폰. 비어도 된다
 * @param sellerOrders 장바구니의 판매자별 상품 금액. 판매자는 겹치지 않는다
 */
public record PreviewCouponDiscountRequest(
        @NotNull List<UUID> issuedCouponIds, @NotNull @Valid List<SellerOrder> sellerOrders) {

    /**
     * 하위 주문 하나.
     *
     * @param amount 상품 금액(항목 금액의 합, 원)
     */
    public record SellerOrder(@NotNull UUID sellerId, long amount) {}

    CalculateCouponDiscountCommand toCommand(UUID buyerId) {
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
