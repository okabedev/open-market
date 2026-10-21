package com.openmarket.order.command.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 결제가 끝났다(docs/architecture/context-map.md "order.order"). catalog가 예약을 확정하고, M4부터 fulfillment가 하위
 * 주문별로 배송을 만든다. 배송지가 실리므로 페이로드를 로그에 남기지 않는다(NFR-7).
 *
 * @param amount 결제 금액(원)
 * @param paidAt 결제 시각(토스 승인 시각)
 */
public record OrderPaid(
        UUID orderId,
        UUID buyerId,
        long amount,
        String orderName,
        Instant paidAt,
        List<SellerOrder> sellerOrders,
        ShippingAddress shippingAddress) {

    public static final String TYPE = "OrderPaid";

    /** 결제 완료가 된 주문으로 만든다. */
    public static OrderPaid of(Order order) {
        com.openmarket.order.command.domain.ShippingAddress address = order.shippingAddress();
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
                                                sellerOrder.lines().stream()
                                                        .map(
                                                                line ->
                                                                        new Line(
                                                                                line.skuId(),
                                                                                line.productId(),
                                                                                line.categoryId(),
                                                                                line.unitPrice(),
                                                                                line.quantity(),
                                                                                line.amount()))
                                                        .toList()))
                        .toList(),
                new ShippingAddress(
                        address.recipientName(),
                        address.recipientPhone(),
                        address.zipCode(),
                        address.baseAddress(),
                        address.detailAddress()));
    }

    /**
     * 하위 주문. 판매자 쿠폰 부담분은 쿠폰이 생기는 M5에서 더한다.
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
            UUID skuId,
            UUID productId,
            UUID categoryId,
            long unitPrice,
            int quantity,
            long amount) {}

    /** 주문 당시 배송지. */
    public record ShippingAddress(
            String recipientName,
            String recipientPhone,
            String zipCode,
            String baseAddress,
            @Nullable String detailAddress) {}
}
