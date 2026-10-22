package com.openmarket.notification.query.dto;

/** 조회용 알림 종류. 도메인 {@code NotificationKind}와 상수가 같다(docs/architecture/notification.md "인앱 알림"). */
public enum NotificationKind {
    EMAIL_VERIFICATION,
    ORDER_PAID,
    SHIPMENT_SHIPPED,
    SHIPMENT_DELIVERED,
    CLAIM_APPROVED,
    CLAIM_REJECTED,
    CLAIM_COMPLETED,
    SELLER_REJECTED,
    SELLER_ORDER_RECEIVED,
    CLAIM_REQUESTED,
    SELLER_APPROVED,
    PRODUCT_BANNED,
    CLAIM_MEDIATION_REQUESTED,
    REFUND_FAILED
}
