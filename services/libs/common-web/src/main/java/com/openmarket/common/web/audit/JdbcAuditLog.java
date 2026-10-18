package com.openmarket.common.web.audit;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.core.id.Ids;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.sql.Timestamp;
import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 서비스 스키마의 {@code audit_log} 테이블에 쓴다. 호출한 쪽의 트랜잭션에 참여한다. trace id를 함께 남겨 요청 로그와 잇는다. */
public class JdbcAuditLog implements AuditLog {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final @Nullable Tracer tracer;

    public JdbcAuditLog(JdbcClient jdbc, Clock clock, @Nullable Tracer tracer) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.tracer = tracer;
    }

    @Override
    public void record(AuditEntry entry) {
        jdbc.sql(
                        "INSERT INTO audit_log (id, actor_id, action, target_type, target_id, detail,"
                                + " trace_id, occurred_at) VALUES (:id, :actor, :action, :targetType,"
                                + " :targetId, :detail, :traceId, :occurredAt)")
                .param("id", Ids.newId())
                .param("actor", entry.actorId())
                .param("action", entry.action())
                .param("targetType", entry.targetType())
                .param("targetId", entry.targetId())
                .param("detail", entry.detail())
                .param("traceId", traceId())
                .param("occurredAt", Timestamp.from(clock.instant()))
                .update();
    }

    private @Nullable String traceId() {
        Span span = tracer == null ? null : tracer.currentSpan();
        return span == null ? null : span.context().traceId();
    }
}
