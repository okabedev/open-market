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

    /** 인앱 알림 하나를 읽음으로 남긴다. 이미 읽었으면 처음 읽은 시각을 그대로 둔다. */
    void markRead(UUID notificationId, UUID accountId, Instant now);

    /** 알림함에서 {@code now}까지 온 인앱 알림을 모두 읽음으로 남긴다. */
    void markAllRead(Inbox inbox, Instant now);

    /**
     * {@code before}보다 먼저 만든 알림을 오래된 것부터 {@code limit}개까지 지운다. 보낼 차례를 기다리는 이메일은 남긴다. 지운 수를 돌려준다.
     */
    int deleteCreatedBefore(Instant before, int limit);
}
