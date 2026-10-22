package com.openmarket.notification.infra.persistence;

import com.openmarket.notification.command.domain.Inbox;
import com.openmarket.notification.command.domain.Notification;
import com.openmarket.notification.command.domain.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** {@link NotificationRepository}의 Spring Data JPA 구현. */
public interface JpaNotificationRepository
        extends Repository<Notification, UUID>, NotificationRepository {

    @Override
    @Query(
            value =
                    "SELECT id FROM notification WHERE status = 'PENDING' AND channel = 'EMAIL'"
                            + " AND next_attempt_at <= :now ORDER BY next_attempt_at LIMIT :limit",
            nativeQuery = true)
    List<UUID> findDueEmailIds(Instant now, int limit);

    @Override
    @Modifying
    @Query(
            value =
                    "INSERT INTO notification_read (notification_id, account_id, read_at)"
                            + " VALUES (:notificationId, :accountId, :now) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void markRead(UUID notificationId, UUID accountId, Instant now);

    @Override
    default void markAllRead(Inbox inbox, Instant now) {
        insertReadsForInbox(inbox.audience().name(), inbox.accountId(), now);
    }

    /** 관리자 알림은 받는 사람이 없으므로(DB 검사 제약) 받는 쪽만 맞으면 된다. */
    @Modifying
    @Query(
            value =
                    "INSERT INTO notification_read (notification_id, account_id, read_at)"
                            + " SELECT n.id, :accountId, :now FROM notification n"
                            + " WHERE n.channel = 'IN_APP' AND n.audience = :audience"
                            + " AND (n.audience = 'ADMIN' OR n.recipient_account_id = :accountId)"
                            + " AND n.created_at <= :now ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void insertReadsForInbox(String audience, UUID accountId, Instant now);

    @Override
    @Modifying
    @Query(
            value =
                    "DELETE FROM notification WHERE id IN (SELECT id FROM notification"
                            + " WHERE created_at < :before AND status <> 'PENDING'"
                            + " ORDER BY created_at LIMIT :limit)",
            nativeQuery = true)
    int deleteCreatedBefore(Instant before, int limit);
}
