package com.openmarket.fulfillment.command.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 하위 주문을 구매확정했다(docs/architecture/context-map.md "fulfillment.shipment"). order가 진행 상태를 바꾸고, M5에서
 * review(작성 자격)와 settlement(정산 대상)가 받는다. 판매자 쿠폰 부담분은 M5에서 더한다.
 *
 * @param mode 확정 방식({@link ConfirmMode})
 */
public record PurchaseConfirmed(
        UUID orderId,
        UUID sellerOrderId,
        UUID sellerId,
        UUID buyerId,
        String mode,
        Instant confirmedAt,
        List<Line> lines) {

    public static final String TYPE = "PurchaseConfirmed";

    /** 확정한 배송으로 만든다. */
    public static PurchaseConfirmed of(Shipment shipment, PurchaseConfirmation confirmation) {
        return new PurchaseConfirmed(
                shipment.orderId(),
                shipment.sellerOrderId(),
                shipment.sellerId(),
                shipment.buyerId(),
                confirmation.mode().name(),
                confirmation.confirmedAt(),
                shipment.lines().stream()
                        .map(
                                line ->
                                        new Line(
                                                line.orderLineId(),
                                                line.productId(),
                                                line.categoryId(),
                                                line.amount()))
                        .toList());
    }

    /**
     * 주문 항목. 리뷰는 주문 항목 단위이고, 정산 수수료는 카테고리별이다.
     *
     * @param amount 항목 금액(원)
     */
    public record Line(UUID orderLineId, UUID productId, UUID categoryId, long amount) {}
}
