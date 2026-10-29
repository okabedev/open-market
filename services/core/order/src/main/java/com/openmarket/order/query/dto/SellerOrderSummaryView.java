package com.openmarket.order.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 판매자 하위 주문 목록의 한 줄(ORD-4).
 *
 * @param status 주문 상태
 * @param progress 하위 주문 진행 상태(docs/architecture/order.md "하위 주문 진행 상태")
 * @param claim 마지막 클레임. 없으면 비어 있다
 * @param amount 상품 금액(원)
 * @param sellerCouponDiscount 판매자 쿠폰 할인(원). 정산에서 판매자가 부담한다. 쿠폰이 없으면 0
 * @param platformCouponDiscount 플랫폼 쿠폰 할인 중 이 하위 주문의 몫(원). 플랫폼이 부담한다. 쿠폰이 없으면 0
 * @param paymentAmount 하위 주문 결제 금액(원). 상품 금액 − 두 할인
 * @param firstProductName 첫 항목의 상품명(주문 당시)
 * @param lineCount 하위 주문의 항목 수
 * @param paidAt 결제 시각. 목록은 이 순서다
 */
public record SellerOrderSummaryView(
        UUID sellerOrderId,
        UUID orderId,
        OrderStatus status,
        @Nullable SellerOrderProgress progress,
        @Nullable SellerOrderClaimView claim,
        long amount,
        long sellerCouponDiscount,
        long platformCouponDiscount,
        long paymentAmount,
        String firstProductName,
        int lineCount,
        Instant placedAt,
        Instant paidAt) {}
