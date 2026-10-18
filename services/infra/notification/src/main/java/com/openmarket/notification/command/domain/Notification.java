package com.openmarket.notification.command.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 알림(docs/glossary.md). 원인 이벤트 하나와 채널 하나에 알림 하나만 만든다(NTF-2).
 *
 * <p>이메일은 받자마자 보내지 않고 {@code PENDING}으로 저장한 뒤 발송 작업이 보낸다. 이벤트 처리와 저장은 한 트랜잭션이고, SMTP는 그 트랜잭션 밖이다.
 * 실패하면 30초부터 두 배씩 늘려 다시 시도하고, 5번째 실패에서 {@code FAILED}가 된다.
 *
 * <p>{@code params}는 템플릿에 넣을 값이다(인증 링크처럼 비밀이 들어갈 수 있다). 발송을 끝내면(성공이든 최종 실패든) 지운다.
 */
@Entity
@Table(name = "notification")
public class Notification {

    static final int MAX_ATTEMPTS = 5;
    static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(30);

    @Id private UUID id;

    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationKind kind;

    @Column(name = "recipient_account_id", nullable = false)
    private UUID recipientAccountId;

    @Column(name = "recipient_email")
    private @Nullable String recipientEmail;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "notification_param",
            joinColumns = @JoinColumn(name = "notification_id"))
    @MapKeyColumn(name = "name")
    @Column(name = "value", nullable = false, length = 2000)
    private Map<String, String> params = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private @Nullable Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version private long version;

    protected Notification() {}

    private Notification(
            UUID id,
            UUID sourceEventId,
            NotificationChannel channel,
            NotificationKind kind,
            UUID recipientAccountId,
            @Nullable String recipientEmail,
            Map<String, String> params,
            Instant now) {
        this.id = id;
        this.sourceEventId = sourceEventId;
        this.channel = channel;
        this.kind = kind;
        this.recipientAccountId = recipientAccountId;
        this.recipientEmail = recipientEmail;
        this.params = new HashMap<>(params);
        this.status = NotificationStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public static Notification email(
            UUID id,
            UUID sourceEventId,
            NotificationKind kind,
            UUID recipientAccountId,
            String recipientEmail,
            Map<String, String> params,
            Instant now) {
        return new Notification(
                id,
                sourceEventId,
                NotificationChannel.EMAIL,
                kind,
                recipientAccountId,
                recipientEmail,
                params,
                now);
    }

    /** 지금 보낼 차례인가. */
    public boolean isDue(Instant now) {
        return status == NotificationStatus.PENDING && !now.isBefore(nextAttemptAt);
    }

    public void markSent(Instant now) {
        status = NotificationStatus.SENT;
        sentAt = now;
        params.clear();
    }

    public void markFailed(Instant now) {
        attempts += 1;
        if (attempts >= MAX_ATTEMPTS) {
            status = NotificationStatus.FAILED;
            params.clear();
            return;
        }
        nextAttemptAt = now.plus(FIRST_RETRY_DELAY.multipliedBy(1L << (attempts - 1)));
    }

    public UUID id() {
        return id;
    }

    public UUID sourceEventId() {
        return sourceEventId;
    }

    public NotificationChannel channel() {
        return channel;
    }

    public NotificationKind kind() {
        return kind;
    }

    public UUID recipientAccountId() {
        return recipientAccountId;
    }

    public @Nullable String recipientEmail() {
        return recipientEmail;
    }

    public Map<String, String> params() {
        return Collections.unmodifiableMap(params);
    }

    public NotificationStatus status() {
        return status;
    }
}
