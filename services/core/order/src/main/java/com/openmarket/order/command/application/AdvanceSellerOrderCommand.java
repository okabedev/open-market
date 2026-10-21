package com.openmarket.order.command.application;

import java.util.UUID;

/** fulfillment 배송 이벤트로 하위 주문 진행 상태를 바꾼다(docs/architecture/order.md "하위 주문 진행 상태"). */
public record AdvanceSellerOrderCommand(UUID orderId, UUID sellerOrderId, Stage stage) {

    /** 배송 이벤트가 알린 단계. 상수는 도메인 {@code SellerOrderProgress}의 같은 이름이다. */
    public enum Stage {
        ACCEPTED,
        SHIPPED,
        IN_TRANSIT,
        DELIVERED
    }
}
