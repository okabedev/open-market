package com.openmarket.order.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 구매자 주문 목록의 한 줄(ORD-3).
 *
 * @param cancelReason 취소됐을 때만
 * @param amount 결제 금액(원)
 */
public record OrderSummaryView(
        UUID orderId,
        OrderStatus status,
        @Nullable CancelReason cancelReason,
        long amount,
        String orderName,
        Instant placedAt) {}
