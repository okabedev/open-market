package com.openmarket.notification.query.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 알림함의 인앱 알림 하나.
 *
 * @param message 보일 문구. 앱은 그대로 보인다
 * @param targetType 누르면 이동할 대상 종류. 없으면 {@code null}
 * @param read 보는 회원이 읽었는가. 관리자 알림은 관리자마다 다르다
 */
public record NotificationView(
        UUID notificationId,
        NotificationKind kind,
        String message,
        @Nullable NotificationTarget targetType,
        @Nullable UUID targetId,
        boolean read,
        Instant createdAt) {}
