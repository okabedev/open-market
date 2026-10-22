package com.openmarket.notification.query.dao;

import static com.openmarket.notification.command.domain.QNotification.notification;
import static com.openmarket.notification.command.domain.QNotificationRead.notificationRead;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import com.openmarket.notification.query.dto.CursorPageView;
import com.openmarket.notification.query.dto.NotificationAudience;
import com.openmarket.notification.query.dto.NotificationKind;
import com.openmarket.notification.query.dto.NotificationTarget;
import com.openmarket.notification.query.dto.NotificationView;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLSubQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 알림함 조회(docs/architecture/notification.md "인앱 알림"). 관리자 알림함은 받는 사람이 없는 {@code ADMIN} 알림이고, 읽음은 보는
 * 회원의 기록으로 정한다. 인덱스는 {@code notification_inbox_idx}다.
 */
@Repository
public class NotificationQueryDao {

    private final JPAQueryFactory query;

    public NotificationQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    /**
     * 알림함 목록. 최근 순이고, 시각이 같으면 알림 ID 역순이다.
     *
     * @param cursor 이전 쪽의 {@code nextCursor}. 형식이 틀리면 400 {@code NOTIFICATION_INVALID_CURSOR}
     */
    public CursorPageView<NotificationView> findInbox(
            NotificationAudience audience, UUID accountId, @Nullable String cursor, int size) {
        BooleanBuilder where = new BooleanBuilder(inbox(audience, accountId));
        if (cursor != null) {
            Cursor after = Cursor.decode(cursor);
            where.and(
                    notification
                            .createdAt
                            .lt(after.createdAt())
                            .or(
                                    notification
                                            .createdAt
                                            .eq(after.createdAt())
                                            .and(notification.id.lt(after.id()))));
        }
        BooleanExpression read = readBy(accountId).exists();
        List<NotificationView> rows =
                query
                        .select(
                                notification.id,
                                notification.kind.stringValue(),
                                notification.message,
                                notification.targetType.stringValue(),
                                notification.targetId,
                                read,
                                notification.createdAt)
                        .from(notification)
                        .where(where)
                        .orderBy(notification.createdAt.desc(), notification.id.desc())
                        .limit(size + 1L)
                        .fetch()
                        .stream()
                        .map(
                                t -> {
                                    String target = t.get(3, String.class);
                                    return new NotificationView(
                                            Objects.requireNonNull(t.get(notification.id)),
                                            NotificationKind.valueOf(
                                                    Objects.requireNonNull(t.get(1, String.class))),
                                            Objects.requireNonNull(t.get(notification.message)),
                                            target == null
                                                    ? null
                                                    : NotificationTarget.valueOf(target),
                                            t.get(notification.targetId),
                                            Boolean.TRUE.equals(t.get(read)),
                                            Objects.requireNonNull(t.get(notification.createdAt)));
                                })
                        .toList();
        boolean hasNext = rows.size() > size;
        List<NotificationView> items = hasNext ? rows.subList(0, size) : rows;
        Long total =
                query.select(notification.count())
                        .from(notification)
                        .where(inbox(audience, accountId))
                        .fetchOne();
        String nextCursor =
                hasNext
                        ? Cursor.encode(
                                new Cursor(
                                        items.getLast().createdAt(),
                                        items.getLast().notificationId()))
                        : null;
        return new CursorPageView<>(List.copyOf(items), total == null ? 0 : total, nextCursor);
    }

    /** 알림함의 안 읽은 알림 수. */
    public long countUnread(NotificationAudience audience, UUID accountId) {
        Long count =
                query.select(notification.count())
                        .from(notification)
                        .where(inbox(audience, accountId), readBy(accountId).notExists())
                        .fetchOne();
        return count == null ? 0 : count;
    }

    private static BooleanExpression inbox(NotificationAudience audience, UUID accountId) {
        BooleanExpression where =
                notification
                        .channel
                        .stringValue()
                        .eq("IN_APP")
                        .and(notification.audience.stringValue().eq(audience.name()));
        return audience == NotificationAudience.ADMIN
                ? where.and(notification.recipientAccountId.isNull())
                : where.and(notification.recipientAccountId.eq(accountId));
    }

    private static JPQLSubQuery<Integer> readBy(UUID accountId) {
        return JPAExpressions.selectOne()
                .from(notificationRead)
                .where(
                        notificationRead.notificationId.eq(notification.id),
                        notificationRead.accountId.eq(accountId));
    }

    record Cursor(Instant createdAt, UUID id) {

        private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
        private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
        private static final int MAX_LENGTH = 200;

        static String encode(Cursor cursor) {
            return ENCODER.encodeToString(
                    (cursor.createdAt() + "|" + cursor.id()).getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String cursor) {
            if (cursor.length() > MAX_LENGTH) {
                throw invalid();
            }
            try {
                String text = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
                int separator = text.indexOf('|');
                if (separator < 0) {
                    throw invalid();
                }
                return new Cursor(
                        Instant.parse(text.substring(0, separator)),
                        UUID.fromString(text.substring(separator + 1)));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw invalid();
            }
        }

        private static BusinessException invalid() {
            return new BusinessException(
                    ErrorType.INVALID, "NOTIFICATION_INVALID_CURSOR", "알림 목록 커서가 올바르지 않습니다.");
        }
    }
}
