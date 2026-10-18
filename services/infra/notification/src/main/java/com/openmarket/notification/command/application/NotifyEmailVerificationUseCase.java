package com.openmarket.notification.command.application;

import com.openmarket.common.core.id.Ids;
import com.openmarket.notification.command.domain.Notification;
import com.openmarket.notification.command.domain.NotificationChannel;
import com.openmarket.notification.command.domain.NotificationKind;
import com.openmarket.notification.command.domain.NotificationRepository;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 메일 알림을 {@code PENDING}으로 저장한다(NTF-1). 발송은 {@link SendEmailUseCase}가 한다. 같은 원인 이벤트로는 하나만
 * 만든다(NTF-2). 리스너의 멱등 기록과 DB 유니크 제약(원인 이벤트, 채널)이 한 번 더 막는다.
 */
@Service
public class NotifyEmailVerificationUseCase {

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotifyEmailVerificationUseCase(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public void execute(NotifyEmailVerificationCommand command) {
        if (notifications.existsBySourceEventIdAndChannel(
                command.sourceEventId(), NotificationChannel.EMAIL)) {
            return;
        }
        notifications.save(
                Notification.email(
                        Ids.newId(),
                        command.sourceEventId(),
                        NotificationKind.EMAIL_VERIFICATION,
                        command.accountId(),
                        command.email(),
                        Map.of(
                                "verificationUrl", command.verificationUrl(),
                                "expiresAt", command.expiresAt().toString()),
                        clock.instant()));
    }
}
