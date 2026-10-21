package com.openmarket.fulfillment.command.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 결제된 주문의 하위 주문마다 배송을 만든다({@code OrderPaid}).
 *
 * @param paidAt 결제 시각(토스 승인 시각)
 */
public record CreateShipmentsCommand(
        UUID orderId, UUID buyerId, Instant paidAt, List<SellerOrder> sellerOrders) {

    /**
     * 하위 주문.
     *
     * @param amount 하위 주문 금액(원)
     */
    public record SellerOrder(UUID sellerOrderId, UUID sellerId, long amount, List<Line> lines) {}

    /**
     * 주문 항목. 주문 당시 스냅샷이다.
     *
     * @param unitPrice 개당 가격(원)
     */
    public record Line(
            UUID orderLineId,
            UUID skuId,
            UUID productId,
            String productName,
            List<String> optionValues,
            UUID categoryId,
            long unitPrice,
            int quantity,
            long amount) {}
}
