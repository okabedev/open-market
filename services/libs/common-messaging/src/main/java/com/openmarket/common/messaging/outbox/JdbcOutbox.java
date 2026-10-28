package com.openmarket.common.messaging.outbox;

import com.openmarket.common.core.id.Ids;
import com.openmarket.common.messaging.metrics.MessagingMetrics;
import com.openmarket.common.messaging.trace.TraceparentSupplier;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * PostgreSQL {@code outbox_event} 테이블 구현. 테이블은 Flyway 위치 {@code classpath:db/common/messaging}이
 * 만든다. 커밋된 이벤트는 이름별로 센다({@link MessagingMetrics#OUTBOX_EVENTS}).
 */
public class JdbcOutbox implements Outbox {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final TraceparentSupplier traceparent;
    private final Clock clock;
    private final MessagingMetrics metrics;

    public JdbcOutbox(
            JdbcClient jdbc,
            JsonMapper jsonMapper,
            TraceparentSupplier traceparent,
            Clock clock,
            MessagingMetrics metrics) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.traceparent = traceparent;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Override
    public UUID append(
            String aggregateType,
            String aggregateId,
            String type,
            int schemaVersion,
            Object payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("outbox 이벤트는 도메인 변경과 같은 트랜잭션에서 쌓아야 한다: " + type);
        }
        UUID id = Ids.newId();
        jdbc.sql(
                        """
                        INSERT INTO outbox_event
                            (id, aggregate_type, aggregate_id, type, payload, schema_version, traceparent, occurred_at)
                        VALUES (:id, :aggregateType, :aggregateId, :type, CAST(:payload AS jsonb), :schemaVersion,
                                :traceparent, :occurredAt)
                        """)
                .param("id", id)
                .param("aggregateType", aggregateType)
                .param("aggregateId", aggregateId)
                .param("type", type)
                .param("payload", jsonMapper.writeValueAsString(payload))
                .param("schemaVersion", schemaVersion)
                .param("traceparent", traceparent.current())
                .param("occurredAt", Timestamp.from(clock.instant()))
                .update();
        metrics.outboxAppended(type);
        return id;
    }
}
