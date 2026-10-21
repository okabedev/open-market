package com.openmarket.order.query.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 판매자 하위 주문 목록의 한 줄(ORD-4).
 *
 * @param status 주문 상태. M3 하위 주문은 주문 상태를 따른다
 * @param amount 하위 주문 금액(원)
 * @param firstProductName 첫 항목의 상품명(주문 당시)
 * @param lineCount 하위 주문의 항목 수
 * @param paidAt 결제 시각. 목록은 이 순서다
 */
public record SellerOrderSummaryView(
        UUID sellerOrderId,
        UUID orderId,
        OrderStatus status,
        long amount,
        String firstProductName,
        int lineCount,
        Instant placedAt,
        Instant paidAt) {}
