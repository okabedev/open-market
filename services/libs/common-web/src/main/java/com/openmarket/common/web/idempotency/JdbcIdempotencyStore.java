package com.openmarket.common.web.idempotency;

import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * PostgreSQL {@code idempotency_record} 테이블 구현. 동시 요청은 기본 키 {@code (principal, idempotency_key)}로
 * 막는다. 테이블은 Flyway 위치 {@code classpath:db/common/web}이 만든다.
 */
public class JdbcIdempotencyStore implements IdempotencyStore {

    /** 처리 중 기록이 이보다 오래되면 앞선 요청이 중단된 것으로 보고 이어받는다. */
    static final Duration STALE_IN_PROGRESS = Duration.ofMinutes(1);

    /** 완료 기록의 보관 기간. 이보다 오래된 키는 새 요청으로 처리한다. */
    static final Duration RETENTION = Duration.ofHours(24);

    private static final int MAX_ATTEMPTS = 3;

    private final JdbcClient jdbc;

    public JdbcIdempotencyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Begin begin(String principal, String key, String requestHash) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (insert(principal, key, requestHash) || takeOver(principal, key, requestHash)) {
                return new Started();
            }
            Optional<Row> row = find(principal, key);
            if (row.isEmpty()) {
                continue; // 조회 직전에 지워졌다. 다시 시도한다.
            }
            Row existing = row.get();
            if (existing.status().equals("IN_PROGRESS")) {
                return new InProgress();
            }
            if (!existing.requestHash().equals(requestHash)) {
                return new Mismatch();
            }
            return new Completed(existing.responseStatus(), existing.responseBody());
        }
        return new InProgress();
    }

    @Override
    public void complete(String principal, String key, int status, @Nullable String jsonBody) {
        jdbc.sql(
                        """
                        UPDATE idempotency_record
                           SET status = 'COMPLETED', response_status = :status, response_body = CAST(:body AS jsonb)
                         WHERE principal = :principal AND idempotency_key = :key
                        """)
                .param("status", status)
                .param("body", jsonBody)
                .param("principal", principal)
                .param("key", key)
                .update();
    }

    @Override
    public void release(String principal, String key) {
        jdbc.sql(
                        """
                        DELETE FROM idempotency_record
                         WHERE principal = :principal AND idempotency_key = :key AND status = 'IN_PROGRESS'
                        """)
                .param("principal", principal)
                .param("key", key)
                .update();
    }

    @Override
    public int deleteOlderThan(Duration retention) {
        return jdbc.sql(
                        "DELETE FROM idempotency_record WHERE created_at < now() - make_interval(secs => :seconds)")
                .param("seconds", retention.toSeconds())
                .update();
    }

    private boolean insert(String principal, String key, String requestHash) {
        return jdbc.sql(
                                """
                                INSERT INTO idempotency_record (principal, idempotency_key, request_hash, status, created_at)
                                VALUES (:principal, :key, :hash, 'IN_PROGRESS', now())
                                ON CONFLICT DO NOTHING
                                """)
                        .param("principal", principal)
                        .param("key", key)
                        .param("hash", requestHash)
                        .update()
                == 1;
    }

    /** 중단된 처리 중 기록이나 보관 기간이 지난 기록을 새 요청으로 바꾼다. */
    private boolean takeOver(String principal, String key, String requestHash) {
        return jdbc.sql(
                                """
                                UPDATE idempotency_record
                                   SET request_hash = :hash, status = 'IN_PROGRESS', response_status = NULL,
                                       response_body = NULL, created_at = now()
                                 WHERE principal = :principal AND idempotency_key = :key
                                   AND ((status = 'IN_PROGRESS' AND created_at < now() - make_interval(secs => :stale))
                                        OR created_at < now() - make_interval(secs => :retention))
                                """)
                        .param("hash", requestHash)
                        .param("principal", principal)
                        .param("key", key)
                        .param("stale", STALE_IN_PROGRESS.toSeconds())
                        .param("retention", RETENTION.toSeconds())
                        .update()
                == 1;
    }

    private Optional<Row> find(String principal, String key) {
        return jdbc.sql(
                        """
                        SELECT request_hash, status, response_status, response_body::text AS response_body
                          FROM idempotency_record
                         WHERE principal = :principal AND idempotency_key = :key
                        """)
                .param("principal", principal)
                .param("key", key)
                .query(
                        (rs, rowNum) ->
                                new Row(
                                        rs.getString("request_hash"),
                                        rs.getString("status"),
                                        rs.getInt("response_status"),
                                        rs.getString("response_body")))
                .optional();
    }

    private record Row(
            String requestHash, String status, int responseStatus, @Nullable String responseBody) {}
}
