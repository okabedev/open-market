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
 *
 * <p>인앱 알림은 받는 쪽({@link NotificationAudience})의 알림함에 들어가고 만들 때 바로 {@code SENT}다. 문구는 만들 때 정해 저장한다.
 * 관리자 알림은 받는 사람 없이 역할에 한 건이다(docs/architecture/notification.md "인앱 알림").
 *
 * <p>회원에게 보내는 이메일({@link #memberEmail})은 받는 주소를 저장하지 않는다. 보낼 때 member에서 조회하고, 회원이 없거나 탈퇴했거나 이메일을
 * 인증하지 않았으면 {@code SKIPPED}로 끝낸다.
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

    @Column(name = "recipient_account_id")
    private @Nullable UUID recipientAccountId;

    @Enumerated(EnumType.STRING)
    private @Nullable NotificationAudience audience;

    private @Nullable String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type")
    private @Nullable NotificationTarget targetType;

    @Column(name = "target_id")
    private @Nullable UUID targetId;

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
            @Nullable UUID recipientAccountId,
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

    /**
     * 회원에게 보내는 이메일 알림. 받는 주소는 보낼 때 member에서 조회한다(저장하지 않는다).
     *
     * @param params 템플릿에 넣을 값. 문구({@code message})와 앱 링크({@code link})
     */
    public static Notification memberEmail(
            UUID id,
            UUID sourceEventId,
            NotificationKind kind,
            NotificationAudience audience,
            UUID recipientAccountId,
            Map<String, String> params,
            Instant now) {
        Notification notification =
                new Notification(
                        id,
                        sourceEventId,
                        NotificationChannel.EMAIL,
                        kind,
                        recipientAccountId,
                        null,
                        params,
                        now);
        notification.audience = audience;
        return notification;
    }

    /**
     * 인앱 알림. 관리자 알림({@code ADMIN})은 받는 사람이 없고, 나머지는 있어야 한다.
     *
     * @param message 앱에 보일 문구. 개인정보를 넣지 않는다
     */
    public static Notification inApp(
            UUID id,
            UUID sourceEventId,
            NotificationKind kind,
            NotificationAudience audience,
            @Nullable UUID recipientAccountId,
            String message,
            @Nullable NotificationTarget targetType,
            @Nullable UUID targetId,
            Instant now) {
        if ((audience == NotificationAudience.ADMIN) != (recipientAccountId == null)) {
            throw new IllegalArgumentException("관리자 알림만 받는 사람이 없다: " + audience);
        }
        Notification notification =
                new Notification(
                        id,
                        sourceEventId,
                        NotificationChannel.IN_APP,
                        kind,
                        recipientAccountId,
                        null,
                        Map.of(),
                        now);
        notification.audience = audience;
        notification.message = message;
        notification.targetType = targetType;
        notification.targetId = targetId;
        notification.status = NotificationStatus.SENT;
        notification.sentAt = now;
        return notification;
    }

    /** 이 알림함에 있는 인앱 알림인가. */
    public boolean isIn(Inbox inbox) {
        if (channel != NotificationChannel.IN_APP || audience != inbox.audience()) {
            return false;
        }
        return audience == NotificationAudience.ADMIN
                || inbox.accountId().equals(recipientAccountId);
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

    /** 보내지 않기로 했다(받는 회원이 없거나 탈퇴했거나 이메일을 인증하지 않았다). 다시 시도하지 않는다. */
    public void markSkipped() {
        status = NotificationStatus.SKIPPED;
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

    public @Nullable UUID recipientAccountId() {
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

    public @Nullable NotificationAudience audience() {
        return audience;
    }

    public @Nullable String message() {
        return message;
    }
}
