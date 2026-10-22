package com.openmarket.notification.command.application;

import java.util.UUID;

/**
 * 알림함의 인앱 알림을 모두 읽음으로.
 *
 * @param audience 알림함의 받는 쪽(API 경로로 정한다)
 * @param accountId 읽는 회원(토큰의 {@code sub})
 */
public record ReadAllNotificationsCommand(
        ReadNotificationCommand.Audience audience, UUID accountId) {}
