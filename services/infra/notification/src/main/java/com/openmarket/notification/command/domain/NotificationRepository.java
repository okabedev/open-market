package com.openmarket.notification.command.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 알림 저장소. 구현은 {@code infra}에 있다. */
public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    boolean existsBySourceEventIdAndChannel(UUID sourceEventId, NotificationChannel channel);

    /** 지금 보낼 차례인 이메일 알림 ID. 오래된 것부터. */
    List<UUID> findDueEmailIds(Instant now, int limit);
}
