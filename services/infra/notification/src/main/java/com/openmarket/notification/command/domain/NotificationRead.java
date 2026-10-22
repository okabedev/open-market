package com.openmarket.notification.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 알림 읽음(docs/glossary.md). 회원이 인앱 알림 하나를 읽은 기록이다. 관리자 알림은 관리자마다 따로 남는다. 쓰기는 {@link
 * NotificationRepository#markRead}가 한다(이미 있으면 그대로 둔다).
 */
@Entity
@Table(name = "notification_read")
@IdClass(NotificationRead.Key.class)
public class NotificationRead {

    @Id
    @Column(name = "notification_id")
    private UUID notificationId;

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    protected NotificationRead() {}

    /** 복합 키. */
    public static class Key implements Serializable {

        private static final long serialVersionUID = 1L;

        private @Nullable UUID notificationId;
        private @Nullable UUID accountId;

        protected Key() {}

        @Override
        public boolean equals(@Nullable Object other) {
            return other instanceof Key key
                    && Objects.equals(notificationId, key.notificationId)
                    && Objects.equals(accountId, key.accountId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(notificationId, accountId);
        }
    }
}
