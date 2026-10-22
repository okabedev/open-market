package com.openmarket.notification.command.domain;

/** 인앱 알림을 누르면 이동할 대상의 종류. 대상 ID와 함께 저장하고, 앱이 화면 주소를 정한다. */
public enum NotificationTarget {
    ORDER,
    SELLER_ORDER,
    CLAIM,
    REFUND,
    PRODUCT,
    SELLER_APPLICATION
}
