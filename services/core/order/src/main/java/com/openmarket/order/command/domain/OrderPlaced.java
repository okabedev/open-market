package com.openmarket.order.command.domain;

import java.util.List;
import java.util.UUID;

/**
 * 주문을 {@code PENDING}으로 만들었다(docs/architecture/context-map.md "order.order"). catalog가 재고를 예약한다.
 *
 * @param issuedCouponIds 주문에 쓴 발급 쿠폰. 쿠폰은 M5라 M3에서는 비어 있다
 */
public record OrderPlaced(
        UUID orderId, UUID buyerId, List<Line> lines, List<UUID> issuedCouponIds) {

    public static final String TYPE = "OrderPlaced";

    public static OrderPlaced of(Order order) {
        return new OrderPlaced(
                order.id(),
                order.buyerId(),
                order.lines().stream()
                        .map(line -> new Line(line.skuId(), line.quantity()))
                        .toList(),
                List.of());
    }

    public record Line(UUID skuId, int quantity) {}
}
