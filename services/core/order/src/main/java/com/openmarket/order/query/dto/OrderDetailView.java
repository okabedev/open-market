package com.openmarket.order.query.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 구매자 본인 주문 상세(ORD-3). 하위 주문은 판매자가 처음 나온 순서, 항목은 주문한 순서다. 상품 정보는 주문 당시 스냅샷이다.
 *
 * @param cancelReason 취소됐을 때만
 * @param amount 결제 금액(원). M3는 배송비가 없다
 * @param paidAt 결제 시각(토스 승인 시각). 결제됐을 때만
 */
public record OrderDetailView(
        UUID orderId,
        OrderStatus status,
        @Nullable CancelReason cancelReason,
        long amount,
        String orderName,
        Shipping shipping,
        List<SellerOrder> sellerOrders,
        Instant placedAt,
        @Nullable Instant paidAt) {

    /** 주문 당시 배송지. */
    @Schema(name = "OrderShippingView")
    public record Shipping(
            String recipientName,
            String recipientPhone,
            String zipCode,
            String baseAddress,
            @Nullable String detailAddress) {}

    /**
     * 하위 주문.
     *
     * @param progress 진행 상태(docs/architecture/order.md "하위 주문 진행 상태"). 결제 전이면 비어 있다
     * @param claim 마지막 클레임. 없으면 비어 있다
     */
    @Schema(name = "OrderSellerOrderView")
    public record SellerOrder(
            UUID sellerOrderId,
            UUID sellerId,
            String storeName,
            long amount,
            @Nullable SellerOrderProgress progress,
            @Nullable SellerOrderClaimView claim,
            List<Line> lines) {}

    /**
     * 주문 항목.
     *
     * @param optionNames 옵션 이름. {@code optionValues}가 이 순서다
     * @param unitPrice 주문 당시 개당 가격(원)
     */
    @Schema(name = "OrderLineView")
    public record Line(
            UUID orderLineId,
            UUID skuId,
            UUID productId,
            String productName,
            List<String> optionNames,
            List<String> optionValues,
            long unitPrice,
            int quantity,
            long amount) {}
}
