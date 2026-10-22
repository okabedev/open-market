package com.openmarket.notification.command.domain;

/** 인앱 알림의 받는 쪽(docs/glossary.md). 앱 하나와 같다. {@code ADMIN}은 관리자 전체가 함께 보는 역할 대상 알림이라 받는 사람이 없다. */
public enum NotificationAudience {
    BUYER,
    SELLER,
    ADMIN
}
