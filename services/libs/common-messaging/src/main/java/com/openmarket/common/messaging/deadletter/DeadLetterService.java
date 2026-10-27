package com.openmarket.common.messaging.deadletter;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * DLT 메시지 목록, 다시 보내기, 버리기(docs/architecture/messaging.md "DLT 재처리"). 다시 보내기와 버리기는 행을 잠그고 {@code
 * PENDING}일 때만 하며 감사 로그를 남긴다.
 *
 * <p>다시 보내기는 저장한 키·값·원래 헤더를 원래 토픽의 원래 파티션에 보낸다. {@code kafka_dlt-*} 헤더는 빼고 {@value
 * IdempotentEventProcessor#TARGET_GROUP_HEADER}에 실패한 그룹을 적는다. 전송이 끝나야 {@code RETRIED}로 바꾼다. 전송이
 * 실패하면 상태를 바꾸지 않고 503이다. 전송은 됐는데 상태 저장이 실패하면 한 번 더 보낼 수 있고, 대상 그룹의 멱등 소비가 거른다.
 */
public class DeadLetterService {

    public static final String AUDIT_TARGET = "DEAD_LETTER";
    public static final String RETRIED_ACTION = "DEAD_LETTER_RETRIED";
    public static final String DISCARDED_ACTION = "DEAD_LETTER_DISCARDED";

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_REASON_LENGTH = 500;

    /** 다시 보내기가 행 잠금을 잡고 기다리는 최대 시간. */
    static final long SEND_TIMEOUT_SECONDS = 10;

    private static final String DLT_HEADER_PREFIX = "kafka_dlt-";
    private static final TypeReference<List<Map<String, @Nullable Object>>> HEADERS =
            new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final KafkaOperations<String, String> kafka;
    private final TransactionOperations transaction;
    private final ObjectProvider<AuditLog> auditLog;
    private final JsonMapper json;
    private final Clock clock;

    public DeadLetterService(
            JdbcClient jdbc,
            KafkaOperations<String, String> kafka,
            TransactionOperations transaction,
            ObjectProvider<AuditLog> auditLog,
            JsonMapper json,
            Clock clock) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.transaction = transaction;
        this.auditLog = auditLog;
        this.json = json;
        this.clock = clock;
    }

    /**
     * 쌓인 시각 내림차순 목록. 페이로드와 예외 메시지는 주지 않는다(개인정보가 들어갈 수 있다).
     *
     * @param status {@code PENDING}, {@code RETRIED}, {@code DISCARDED} 중 하나. 없으면 모두
     * @param topic 원래 토픽. 없으면 모두
     * @param before 이전 쪽의 {@code nextCursor}
     */
    public DeadLetterPage list(
            @Nullable String status, @Nullable String topic, @Nullable String before, int size) {
        if (status != null && !List.of("PENDING", "RETRIED", "DISCARDED").contains(status)) {
            throw invalid("status는 PENDING, RETRIED, DISCARDED 중 하나입니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw invalid("size는 1~" + MAX_PAGE_SIZE + "입니다.");
        }
        Cursor cursor = before == null ? null : Cursor.decode(before);
        List<DeadLetterView> rows =
                jdbc.sql(
                                """
                                SELECT id, consumer_group, event_id, event_type, original_topic,
                                       original_partition, original_offset, exception_class, trace_id,
                                       status, dead_lettered_at, resolved_at, resolved_by, discard_reason
                                FROM dead_letter
                                WHERE (CAST(:status AS varchar) IS NULL OR status = :status)
                                  AND (CAST(:topic AS varchar) IS NULL OR original_topic = :topic)
                                  AND (CAST(:beforeAt AS timestamptz) IS NULL
                                       OR (dead_lettered_at, id) < (:beforeAt, :beforeId))
                                ORDER BY dead_lettered_at DESC, id DESC
                                LIMIT :limit
                                """)
                        .param("status", status, java.sql.Types.VARCHAR)
                        .param("topic", topic, java.sql.Types.VARCHAR)
                        .param(
                                "beforeAt",
                                cursor == null ? null : cursor.at().atOffset(ZoneOffset.UTC),
                                java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                        .param(
                                "beforeId",
                                cursor == null ? null : cursor.id(),
                                java.sql.Types.OTHER)
                        .param("limit", size + 1)
                        .query(
                                (rs, n) ->
                                        new DeadLetterView(
                                                rs.getObject("id", UUID.class),
                                                rs.getString("consumer_group"),
                                                rs.getObject("event_id", UUID.class),
                                                rs.getString("event_type"),
                                                rs.getString("original_topic"),
                                                rs.getInt("original_partition"),
                                                rs.getLong("original_offset"),
                                                rs.getString("exception_class"),
                                                rs.getString("trace_id"),
                                                rs.getString("status"),
                                                rs.getTimestamp("dead_lettered_at").toInstant(),
                                                instant(rs.getTimestamp("resolved_at")),
                                                rs.getString("resolved_by"),
                                                rs.getString("discard_reason")))
                        .list();
        if (rows.size() <= size) {
            return new DeadLetterPage(rows, null);
        }
        List<DeadLetterView> page = rows.subList(0, size);
        DeadLetterView last = page.getLast();
        return new DeadLetterPage(
                List.copyOf(page), new Cursor(last.deadLetteredAt(), last.id()).encode());
    }

    /** 원래 토픽으로 다시 보내고 {@code RETRIED}로 바꾼다. */
    public void retry(UUID id, String actor) {
        transaction.executeWithoutResult(
                status -> {
                    Locked row = lockPending(id);
                    send(row);
                    resolve(id, "RETRIED", actor, null);
                    audit(
                            actor,
                            RETRIED_ACTION,
                            id,
                            row.originalTopic() + "-" + row.originalPartition());
                });
    }

    /** 다시 처리하지 않기로 하고 {@code DISCARDED}로 바꾼다. 메시지는 보내지 않는다. */
    public void discard(UUID id, String actor, @Nullable String reason) {
        String trimmed = reason == null ? "" : reason.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_REASON_LENGTH) {
            throw invalid("사유는 1~" + MAX_REASON_LENGTH + "자입니다.");
        }
        transaction.executeWithoutResult(
                status -> {
                    lockPending(id);
                    resolve(id, "DISCARDED", actor, trimmed);
                    audit(actor, DISCARDED_ACTION, id, trimmed);
                });
    }

    private Locked lockPending(UUID id) {
        Locked row =
                jdbc.sql(
                                """
                                SELECT status, consumer_group, original_topic, original_partition,
                                       message_key, payload, CAST(headers AS text) AS headers
                                FROM dead_letter WHERE id = :id FOR UPDATE
                                """)
                        .param("id", id)
                        .query(
                                (rs, n) ->
                                        new Locked(
                                                rs.getString("status"),
                                                rs.getString("consumer_group"),
                                                rs.getString("original_topic"),
                                                rs.getInt("original_partition"),
                                                rs.getString("message_key"),
                                                rs.getString("payload"),
                                                rs.getString("headers")))
                        .optional()
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "DEAD_LETTER_NOT_FOUND", "DLT 메시지를 찾을 수 없습니다."));
        if (!"PENDING".equals(row.status())) {
            throw new BusinessException(
                    ErrorType.CONFLICT, "DEAD_LETTER_NOT_PENDING", "이미 다시 보냈거나 버린 메시지입니다.");
        }
        return row;
    }

    private void send(Locked row) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(
                        row.originalTopic(),
                        row.originalPartition(),
                        row.messageKey(),
                        row.payload(),
                        headers(row));
        try {
            kafka.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw publishFailed(e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw publishFailed(e);
        }
    }

    /** 저장한 원래 헤더에서 {@code kafka_dlt-*}와 앞서 붙은 대상 그룹을 빼고 이번 대상 그룹을 더한다. */
    private List<Header> headers(Locked row) {
        List<Header> headers = new ArrayList<>();
        for (Map<String, @Nullable Object> entry : json.readValue(row.headers(), HEADERS)) {
            String key = String.valueOf(entry.get("key"));
            if (key.startsWith(DLT_HEADER_PREFIX)
                    || key.equals(IdempotentEventProcessor.TARGET_GROUP_HEADER)) {
                continue;
            }
            Object value = entry.get("value");
            byte[] bytes =
                    value == null
                            ? null
                            : Boolean.TRUE.equals(entry.get("base64"))
                                    ? Base64.getDecoder().decode(value.toString())
                                    : value.toString().getBytes(StandardCharsets.UTF_8);
            headers.add(new RecordHeader(key, bytes));
        }
        headers.add(
                new RecordHeader(
                        IdempotentEventProcessor.TARGET_GROUP_HEADER,
                        row.consumerGroup().getBytes(StandardCharsets.UTF_8)));
        return headers;
    }

    private void resolve(UUID id, String status, String actor, @Nullable String reason) {
        jdbc.sql(
                        """
                        UPDATE dead_letter
                        SET status = :status, resolved_at = :now, resolved_by = :actor, discard_reason = :reason
                        WHERE id = :id
                        """)
                .param("status", status)
                .param("now", Timestamp.from(clock.instant()))
                .param("actor", actor)
                .param("reason", reason, java.sql.Types.VARCHAR)
                .param("id", id)
                .update();
    }

    private void audit(String actor, String action, UUID id, String detail) {
        auditLog.ifAvailable(
                log ->
                        log.record(
                                new AuditEntry(
                                        actor, action, AUDIT_TARGET, id.toString(), detail)));
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorType.INVALID, "DEAD_LETTER_INVALID_INPUT", message);
    }

    private static BusinessException publishFailed(Exception cause) {
        return new BusinessException(
                ErrorType.UNAVAILABLE,
                "DEAD_LETTER_PUBLISH_FAILED",
                "메시지를 다시 보내지 못했습니다. 잠시 뒤 다시 시도해 주세요.",
                cause);
    }

    private static @Nullable Instant instant(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record Locked(
            String status,
            String consumerGroup,
            String originalTopic,
            int originalPartition,
            @Nullable String messageKey,
            @Nullable String payload,
            String headers) {}

    /** 목록 커서. 마지막 행의 쌓인 시각과 ID를 base64url로 감싼다. 클라이언트에는 불투명한 문자열이다. */
    record Cursor(Instant at, UUID id) {

        private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

        String encode() {
            return ENCODER.encodeToString((at + "," + id).getBytes(StandardCharsets.UTF_8));
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
}
