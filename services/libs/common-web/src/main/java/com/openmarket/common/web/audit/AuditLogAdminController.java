package com.openmarket.common.web.audit;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감사 로그 조회(ADM-4, docs/architecture/data.md "audit_log", ADR 0023). 서비스마다 같은 API이고 admin BFF가 여러
 * 서비스의 결과를 시간순으로 합친다. {@code /v1/admin/**}의 ADMIN 역할 검사는 서비스의 보안 설정이 한다.
 *
 * <p>{@code (occurred_at, id)} 내림차순 커서다. 커서는 {@code <시각(ISO-8601)>,<id>}를 base64url(패딩 없음)로 감싼 값이라,
 * admin BFF가 합친 목록의 마지막 행으로 만들어 모든 서비스에 같이 보낸다.
 */
@RestController
public class AuditLogAdminController {

    static final int MAX_PAGE_SIZE = 100;

    private final JdbcClient jdbc;

    public AuditLogAdminController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 감사 로그 목록. 기간은 {@code from} 이상 {@code to} 미만이다.
     *
     * @param actorId 작업한 사람(토큰의 {@code sub})
     * @param action 작업. 예: {@code COUPON_STOPPED}
     * @param before 이전 쪽의 {@code nextCursor}
     */
    @GetMapping("/v1/admin/audit-logs")
    public AuditLogPage listAuditLogs(
            @RequestParam(required = false) @Nullable String from,
            @RequestParam(required = false) @Nullable String to,
            @RequestParam(required = false) @Nullable String actorId,
            @RequestParam(required = false) @Nullable String action,
            @RequestParam(required = false) @Nullable String before,
            @RequestParam(defaultValue = "20") int size) {
        Instant fromAt = instant(from, "from");
        Instant toAt = instant(to, "to");
        if (fromAt != null && toAt != null && !fromAt.isBefore(toAt)) {
            throw invalid("from은 to보다 앞이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw invalid("size는 1~" + MAX_PAGE_SIZE + "입니다.");
        }
        Cursor cursor = before == null ? null : Cursor.decode(before);
        List<AuditLogView> rows =
                jdbc.sql(
                                """
                                SELECT id, actor_id, action, target_type, target_id, detail, trace_id, occurred_at
                                FROM audit_log
                                WHERE (CAST(:fromAt AS timestamptz) IS NULL OR occurred_at >= :fromAt)
                                  AND (CAST(:toAt AS timestamptz) IS NULL OR occurred_at < :toAt)
                                  AND (CAST(:actorId AS varchar) IS NULL OR actor_id = :actorId)
                                  AND (CAST(:action AS varchar) IS NULL OR action = :action)
                                  AND (CAST(:beforeAt AS timestamptz) IS NULL
                                       OR (occurred_at, id) < (:beforeAt, :beforeId))
                                ORDER BY occurred_at DESC, id DESC
                                LIMIT :limit
                                """)
                        .param("fromAt", offset(fromAt), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                        .param("toAt", offset(toAt), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                        .param("actorId", actorId, java.sql.Types.VARCHAR)
                        .param("action", action, java.sql.Types.VARCHAR)
                        .param(
                                "beforeAt",
                                cursor == null ? null : offset(cursor.at()),
                                java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                        .param(
                                "beforeId",
                                cursor == null ? null : cursor.id(),
                                java.sql.Types.OTHER)
                        .param("limit", size + 1)
                        .query(
                                (rs, n) ->
                                        new AuditLogView(
                                                rs.getObject("id", UUID.class),
                                                rs.getString("actor_id"),
                                                rs.getString("action"),
                                                rs.getString("target_type"),
                                                rs.getString("target_id"),
                                                rs.getString("detail"),
                                                rs.getString("trace_id"),
                                                timestamp(rs.getTimestamp("occurred_at"))))
                        .list();
        if (rows.size() <= size) {
            return new AuditLogPage(rows, null);
        }
        List<AuditLogView> page = List.copyOf(rows.subList(0, size));
        AuditLogView last = page.getLast();
        return new AuditLogPage(page, new Cursor(last.occurredAt(), last.id()).encode());
    }

    private static @Nullable Instant instant(@Nullable String value, String name) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw invalid(name + "는 ISO-8601 시각입니다. 예: 2026-10-27T00:00:00Z");
        }
    }

    private static java.time.@Nullable OffsetDateTime offset(@Nullable Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant timestamp(Timestamp timestamp) {
        return timestamp.toInstant();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorType.INVALID, "AUDIT_LOG_INVALID_INPUT", message);
    }

    /** 목록 커서. {@code <시각>,<id>}를 base64url(패딩 없음)로 감싼다. */
    record Cursor(Instant at, UUID id) {

        String encode() {
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString((at + "," + id).getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String value) {
            try {
                String[] parts =
                        new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
                                .split(",", 2);
                return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
            } catch (RuntimeException e) {
                throw invalid("before 커서 형식이 맞지 않습니다.");
            }
        }
    }

    /**
     * 감사 로그 한 줄.
     *
     * @param actorId 작업한 사람(토큰의 {@code sub})
     * @param detail 작업 사유 같은 부가 설명. 개인정보는 넣지 않는다
     * @param traceId 요청의 trace id. 요청 로그와 잇는다
     */
    public record AuditLogView(
            UUID id,
            String actorId,
            String action,
            String targetType,
            String targetId,
            @Nullable String detail,
            @Nullable String traceId,
            Instant occurredAt) {}

    /**
     * 감사 로그 목록 한 쪽.
     *
     * @param nextCursor 다음 쪽을 받을 {@code before} 값. 마지막 쪽이면 비어 있다
     */
    public record AuditLogPage(List<AuditLogView> items, @Nullable String nextCursor) {}
}
