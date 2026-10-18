package com.openmarket.notification.infra.persistence;

import com.openmarket.notification.command.domain.Notification;
import com.openmarket.notification.command.domain.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
}
