package com.openmarket.notification.command.application;

import com.openmarket.notification.command.domain.Notification;
import com.openmarket.notification.command.domain.NotificationChannel;
import com.openmarket.notification.command.domain.NotificationKind;
import com.openmarket.notification.command.domain.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 유스케이스 테스트용 가짜 포트. */
final class Fakes {

    static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");

    private Fakes() {}

    static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    static final class Notifications implements NotificationRepository {
        final Map<UUID, Notification> rows = new LinkedHashMap<>();

        @Override
        public Notification save(Notification notification) {
            rows.put(notification.id(), notification);
            return notification;
        }

        @Override
        public Optional<Notification> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public boolean existsBySourceEventIdAndChannel(
                UUID sourceEventId, NotificationChannel channel) {
            return rows.values().stream()
                    .anyMatch(
                            n -> n.sourceEventId().equals(sourceEventId) && n.channel() == channel);
        }

        @Override
        public List<UUID> findDueEmailIds(Instant now, int limit) {
            return rows.values().stream()
                    .filter(n -> n.isDue(now))
                    .map(Notification::id)
                    .limit(limit)
                    .toList();
        }
    }

    static final class Mailbox implements MailSenderPort {
        final List<MailSenderPort.OutgoingEmail> sent = new ArrayList<>();
        boolean failing;

        @Override
        public void send(MailSenderPort.OutgoingEmail email) {
            if (failing) {
                throw new MailSenderPort.MailDeliveryException("SMTP 연결 실패");
            }
            sent.add(email);
        }
    }

    static final EmailRendererPort RENDERER =
            (NotificationKind kind, Map<String, String> params) ->
                    new EmailRendererPort.RenderedEmail(
                            "제목 " + kind, "<p>" + params + "</p>", "본문 " + params);
}
