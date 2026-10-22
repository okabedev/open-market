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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원인 이벤트 하나로 인앱 알림들을 만든다(NTF-1). 문구는 {@link InAppMessages}가 정한다. 같은 원인 이벤트로는 한 번만 만든다(NTF-2). 알림들을
 * 한 트랜잭션에 저장하므로 하나라도 있으면 이미 처리한 이벤트다. DB 유니크 제약(원인 이벤트, 채널, 받는 쪽, 받는 사람)이 한 번 더 막는다.
 */
@Service
public class NotifyInAppUseCase {

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotifyInAppUseCase(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public void execute(NotifyInAppCommand command) {
        if (notifications.existsBySourceEventIdAndChannel(
                command.sourceEventId(), NotificationChannel.IN_APP)) {
            return;
        }
        Instant now = clock.instant();
        for (NotifyInAppCommand.Notice notice : command.notices()) {
            NotificationKind kind = NotificationKind.valueOf(notice.kind().name());
            NotifyInAppCommand.Target target = notice.target();
            notifications.save(
                    Notification.inApp(
                            Ids.newId(),
                            command.sourceEventId(),
                            kind,
                            NotificationAudience.valueOf(notice.audience().name()),
                            notice.recipientId(),
                            InAppMessages.compose(kind, notice.params()),
                            target == null ? null : NotificationTarget.valueOf(target.name()),
                            notice.targetId(),
                            now));
        }
    }
}
