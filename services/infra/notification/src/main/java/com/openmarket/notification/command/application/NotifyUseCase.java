package com.openmarket.notification.command.application;

import com.openmarket.common.core.id.Ids;
import com.openmarket.notification.command.domain.InAppMessages;
import com.openmarket.notification.command.domain.Notification;
import com.openmarket.notification.command.domain.NotificationAudience;
import com.openmarket.notification.command.domain.NotificationChannel;
import com.openmarket.notification.command.domain.NotificationKind;
import com.openmarket.notification.command.domain.NotificationRepository;
import com.openmarket.notification.command.domain.NotificationTarget;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원인 이벤트 하나로 알림들을 만든다(NTF-1). 알림마다 인앱 알림을 만들고, 이메일 종류({@link NotificationKind#emailed()})면 같은 문구와 앱
 * 링크로 이메일 알림({@code PENDING})도 만든다. 이메일 발송과 받는 주소 조회는 {@link SendEmailUseCase}가 한다.
 *
 * <p>같은 원인 이벤트로는 한 번만 만든다(NTF-2). 알림들을 한 트랜잭션에 저장하므로 인앱 알림이 하나라도 있으면 이미 처리한 이벤트다. DB 유니크 제약(원인 이벤트,
 * 채널, 받는 쪽, 받는 사람)이 한 번 더 막는다.
 */
@Service
public class NotifyUseCase {

    private final NotificationRepository notifications;
    private final AppLinkPort links;
    private final Clock clock;

    public NotifyUseCase(NotificationRepository notifications, AppLinkPort links, Clock clock) {
        this.notifications = notifications;
        this.links = links;
        this.clock = clock;
    }

    @Transactional
    public void execute(NotifyCommand command) {
        if (notifications.existsBySourceEventIdAndChannel(
                command.sourceEventId(), NotificationChannel.IN_APP)) {
            return;
        }
        Instant now = clock.instant();
        for (NotifyCommand.Notice notice : command.notices()) {
            NotificationKind kind = NotificationKind.valueOf(notice.kind().name());
            NotificationAudience audience = NotificationAudience.valueOf(notice.audience().name());
            NotifyCommand.Target target = notice.target();
            NotificationTarget targetType =
                    target == null ? null : NotificationTarget.valueOf(target.name());
            String message = InAppMessages.compose(kind, notice.params());
            notifications.save(
                    Notification.inApp(
                            Ids.newId(),
                            command.sourceEventId(),
                            kind,
                            audience,
                            notice.recipientId(),
                            message,
                            targetType,
                            notice.targetId(),
                            now));
            UUID recipientId = notice.recipientId();
            if (kind.emailed() && recipientId != null) {
                notifications.save(
                        Notification.memberEmail(
                                Ids.newId(),
                                command.sourceEventId(),
                                kind,
                                audience,
                                recipientId,
                                Map.of(
                                        "message",
                                        message,
                                        "link",
                                        links.linkFor(audience, targetType, notice.targetId())),
                                now));
            }
        }
    }
}
