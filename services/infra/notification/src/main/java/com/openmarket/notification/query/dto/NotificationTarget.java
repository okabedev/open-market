package com.openmarket.notification.query.dto;

/** 조회용 이동 대상 종류. 도메인 {@code NotificationTarget}과 상수가 같다. */
public enum NotificationTarget {
    ORDER,
    SELLER_ORDER,
    CLAIM,
    REFUND,
    PRODUCT,
    SELLER_APPLICATION,
    REVIEW,
    SETTLEMENT
}
