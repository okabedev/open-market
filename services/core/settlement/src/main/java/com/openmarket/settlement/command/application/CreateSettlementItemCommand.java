package com.openmarket.settlement.command.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 구매확정한 하위 주문({@code PurchaseConfirmed})으로 정산 대상을 만든다.
 *
 * @param sellerCouponDiscount 판매자 쿠폰 할인(판매자 부담분)
 */
public record CreateSettlementItemCommand(
        UUID orderId,
        UUID sellerOrderId,
        UUID sellerId,
        Instant confirmedAt,
        long sellerCouponDiscount,
        List<Line> lines) {

    /**
     * 주문 항목.
     *
     * @param categoryId 상품의 소분류 카테고리
     * @param amount 개당 가격 × 수량. 쿠폰을 빼기 전이다
     */
    public record Line(
            UUID orderLineId,
            UUID productId,
            String productName,
            UUID categoryId,
            int quantity,
            long amount) {}
}
