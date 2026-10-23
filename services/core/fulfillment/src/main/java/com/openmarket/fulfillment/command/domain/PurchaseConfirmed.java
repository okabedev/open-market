package com.openmarket.fulfillment.command.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 하위 주문을 구매확정했다(docs/architecture/context-map.md "fulfillment.shipment"). order가 진행 상태를 바꾸고,
 * review(작성 자격), settlement(정산 대상), search(판매량)가 받는다.
 *
 * @param mode 확정 방식({@link ConfirmMode})
 * @param sellerCouponDiscount 판매자 쿠폰 할인(원). 판매자 부담분이라 정산에서 뺀다. M5 이전 배송은 0
 */
public record PurchaseConfirmed(
        UUID orderId,
        UUID sellerOrderId,
        UUID sellerId,
        UUID buyerId,
        String mode,
        Instant confirmedAt,
        long sellerCouponDiscount,
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
                shipment.sellerCouponDiscount(),
                shipment.lines().stream()
                        .map(
                                line ->
                                        new Line(
                                                line.orderLineId(),
                                                line.productId(),
                                                line.productName(),
                                                line.optionValues(),
                                                line.categoryId(),
                                                line.quantity(),
                                                line.amount()))
                        .toList());
    }

    /**
     * 주문 항목. 리뷰는 주문 항목 단위이고, 정산 수수료는 카테고리별, 판매량은 수량이다.
     *
     * @param optionValues 옵션 값. 옵션 이름 순서대로, 옵션이 없으면 빈 목록
     * @param amount 항목 금액(원)
     */
    public record Line(
            UUID orderLineId,
            UUID productId,
            String productName,
            List<String> optionValues,
            UUID categoryId,
            int quantity,
            long amount) {}
}
