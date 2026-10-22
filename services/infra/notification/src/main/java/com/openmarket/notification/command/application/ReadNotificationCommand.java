package com.openmarket.notification.command.application;

import java.util.UUID;

/**
 * 인앱 알림 하나를 읽음으로.
 *
 * @param audience 알림함의 받는 쪽(API 경로로 정한다)
 * @param accountId 읽는 회원(토큰의 {@code sub})
 */
public record ReadNotificationCommand(UUID notificationId, Audience audience, UUID accountId) {

    /** 받는 쪽(도메인 {@code NotificationAudience}와 같은 이름). */
    public enum Audience {
        BUYER,
        SELLER,
        ADMIN
    }
}
