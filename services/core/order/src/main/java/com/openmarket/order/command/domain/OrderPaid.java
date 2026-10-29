package com.openmarket.order.command.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 결제가 끝났다(docs/architecture/context-map.md "order.order"). catalog가 예약을 확정하고, M4부터 fulfillment가 하위
 * 주문별로 배송을 만든다. 배송지는 싣지 않는다. 판매자는 발송할 때 하위 주문 상세에서 받는다(NFR-7).
 *
 * @param amount 결제 금액(원). 하위 주문 결제 금액의 합이다
 * @param paidAt 결제 시각(토스 승인 시각)
 */
public record OrderPaid(
        UUID orderId,
        UUID buyerId,
        long amount,
        String orderName,
        Instant paidAt,
        List<SellerOrder> sellerOrders) {

    public static final String TYPE = "OrderPaid";

    /** 결제 완료가 된 주문으로 만든다. */
    public static OrderPaid of(Order order) {
        return new OrderPaid(
                order.id(),
                order.buyerId(),
                order.amount(),
                order.orderName(),
                Objects.requireNonNull(order.paidAt(), "결제 완료 주문에만 만든다"),
                order.sellerOrders().stream()
                        .map(
                                sellerOrder ->
                                        new SellerOrder(
                                                sellerOrder.id(),
                                                sellerOrder.sellerId(),
                                                sellerOrder.amount(),
                                                sellerOrder.sellerCouponDiscount(),
                                                sellerOrder.platformCouponDiscount(),
                                                sellerOrder.paymentAmount(),
                                                sellerOrder.lines().stream()
                                                        .map(
                                                                line ->
                                                                        new Line(
                                                                                line.id(),
                                                                                line.skuId(),
                                                                                line.productId(),
                                                                                line.productName(),
                                                                                line.optionValues(),
                                                                                line.categoryId(),
                                                                                line.unitPrice(),
                                                                                line.quantity(),
                                                                                line.amount()))
                                                        .toList()))
                        .toList());
    }

    /**
     * 하위 주문.
     *
     * @param amount 상품 금액(원). 항목 금액의 합이다
     * @param sellerCouponDiscount 판매자 쿠폰 할인(원). 판매자가 부담한다
     * @param platformCouponDiscount 플랫폼 쿠폰 할인 중 이 하위 주문의 몫(원)
     * @param paymentAmount 하위 주문 결제 금액(원). amount − 두 할인. 환불의 기준이다
     */
    public record SellerOrder(
            UUID sellerOrderId,
            UUID sellerId,
            long amount,
            long sellerCouponDiscount,
            long platformCouponDiscount,
            long paymentAmount,
            List<Line> lines) {}

    /**
     * 주문 항목. 주문 당시 스냅샷이다.
     *
     * @param orderLineId 주문 항목 ID. 구매확정·리뷰가 주문 항목 단위로 쓴다
     * @param optionValues 옵션 값. 옵션 이름 순서대로, 옵션이 없으면 빈 목록
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
