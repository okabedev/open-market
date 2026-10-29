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
 * @param claim 마지막 클레임. 없으면 비어 있다
 * @param amount 상품 금액(원)
 * @param sellerCouponDiscount 판매자 쿠폰 할인(원). 정산에서 판매자가 부담한다. 쿠폰이 없으면 0
 * @param platformCouponDiscount 플랫폼 쿠폰 할인 중 이 하위 주문의 몫(원). 플랫폼이 부담한다. 쿠폰이 없으면 0
 * @param paymentAmount 하위 주문 결제 금액(원). 상품 금액 − 두 할인
 * @param paidAt 결제 시각
 */
public record SellerOrderDetailView(
        UUID sellerOrderId,
        UUID orderId,
        OrderStatus status,
        @Nullable SellerOrderProgress progress,
        @Nullable SellerOrderClaimView claim,
        long amount,
        long sellerCouponDiscount,
        long platformCouponDiscount,
        long paymentAmount,
        OrderDetailView.Shipping shipping,
        List<OrderDetailView.Line> lines,
        Instant placedAt,
        Instant paidAt) {}
