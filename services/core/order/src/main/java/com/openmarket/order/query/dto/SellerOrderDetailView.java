package com.openmarket.order.query.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 판매자 본인 하위 주문 상세(ORD-4). 항목은 주문한 순서이고 상품 정보는 주문 당시 스냅샷이다. 배송지는 발송에 쓴다.
 *
 * @param status 주문 상태
 * @param progress 하위 주문 진행 상태(docs/architecture/order.md "하위 주문 진행 상태")
 * @param amount 하위 주문 금액(원)
 * @param paidAt 결제 시각
 */
public record SellerOrderDetailView(
        UUID sellerOrderId,
        UUID orderId,
        OrderStatus status,
        @Nullable SellerOrderProgress progress,
        long amount,
        OrderDetailView.Shipping shipping,
        List<OrderDetailView.Line> lines,
        Instant placedAt,
        Instant paidAt) {}
