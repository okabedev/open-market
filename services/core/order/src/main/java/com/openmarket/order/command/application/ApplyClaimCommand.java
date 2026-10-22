package com.openmarket.order.command.application;

import java.util.UUID;

/** fulfillment 클레임 이벤트로 하위 주문 클레임을 바꾼다(docs/architecture/order.md "하위 주문 클레임"). */
public record ApplyClaimCommand(
        UUID orderId, UUID sellerOrderId, UUID claimId, Type type, Status status) {

    /** 클레임 유형. 상수는 도메인 {@code ClaimType}의 같은 이름이다. */
    public enum Type {
        CANCEL,
        RETURN,
        EXCHANGE
    }

    /** 클레임 이벤트가 알린 상태. 상수는 도메인 {@code ClaimStatus}의 같은 이름이다. */
    public enum Status {
        REQUESTED,
        APPROVED,
        REJECTED,
        MEDIATING,
        COMPLETED
    }
}
