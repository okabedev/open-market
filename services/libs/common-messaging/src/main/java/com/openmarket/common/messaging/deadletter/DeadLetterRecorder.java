package com.openmarket.common.messaging.deadletter;

import com.openmarket.common.core.id.Ids;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.support.KafkaHeaders;
import tools.jackson.databind.json.JsonMapper;

/**
 * DLT 메시지 하나를 {@code dead_letter}에 넣는다(docs/architecture/data.md "dead_letter"). 같은 DLT 위치(토픽, 파티션,
 * 오프셋)는 한 번만 넣는다.
 *
 * <p>헤더는 {@code [{key, value, base64}]} 목록으로 저장한다. 같은 이름의 헤더가 여럿일 수 있고, 원래 파티션·오프셋처럼 바이너리 값도 있어서 다시
 * 보낼 때 원래 헤더를 그대로 되살리려면 순서와 바이트가 필요하다. UTF-8로 읽을 수 있으면 문자열, 아니면 base64다.
 */
public class DeadLetterRecorder {

    /** Debezium Outbox Event Router가 넣는 헤더(docs/architecture/messaging.md "발행"). */
    static final String ID_HEADER = "id";

    static final String TYPE_HEADER = "type";
    static final String TRACEPARENT_HEADER = "traceparent";

    /** 원래 위치 헤더가 없으면(DLT에 직접 쓴 메시지) 오프셋을 모른다. */
    static final long UNKNOWN_OFFSET = -1;

    private static final String DLT_SUFFIX = ".DLT";
    private static final Pattern TRACEPARENT =
            Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public DeadLetterRecorder(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /**
     * DLT 메시지를 {@code PENDING}으로 넣는다. 원래 위치 헤더가 없으면 DLT 토픽 이름에서 원래 토픽을, DLT 파티션을 원래 파티션으로 쓰고 오프셋은
     * {@value #UNKNOWN_OFFSET}이다.
     *
     * @return 새로 넣었으면 {@code true}, 이미 있는 위치라 넘겼으면 {@code false}
     */
    public boolean record(ConsumerRecord<String, String> record) {
        @Nullable String topicHeader = text(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        String originalTopic = topicHeader != null ? topicHeader : stripSuffix(record.topic());
        Integer originalPartition = intHeader(record, KafkaHeaders.DLT_ORIGINAL_PARTITION);
        Long originalOffset = longHeader(record, KafkaHeaders.DLT_ORIGINAL_OFFSET);
        String exception = text(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN);
        if (exception == null) {
            exception = text(record, KafkaHeaders.DLT_EXCEPTION_FQCN);
        }
        return jdbc.sql(
                                """
                                INSERT INTO dead_letter (
                                    id, consumer_group, dlt_topic, dlt_partition, dlt_offset,
                                    original_topic, original_partition, original_offset,
                                    event_id, event_type, message_key, payload, headers,
                                    exception_class, trace_id, status, dead_lettered_at)
                                VALUES (
                                    :id, :group, :dltTopic, :dltPartition, :dltOffset,
                                    :originalTopic, :originalPartition, :originalOffset,
                                    :eventId, :eventType, :key, :payload, CAST(:headers AS jsonb),
                                    :exception, :traceId, 'PENDING', :deadLetteredAt)
                                ON CONFLICT (dlt_topic, dlt_partition, dlt_offset) DO NOTHING
                                """)
                        .param("id", Ids.newId())
                        .param(
                                "group",
                                nonNull(text(record, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP)))
                        .param("dltTopic", record.topic())
                        .param("dltPartition", record.partition())
                        .param("dltOffset", record.offset())
                        .param("originalTopic", originalTopic)
                        .param(
                                "originalPartition",
                                originalPartition != null ? originalPartition : record.partition())
                        .param(
                                "originalOffset",
                                originalOffset != null ? originalOffset : UNKNOWN_OFFSET)
                        .param("eventId", uuid(text(record, ID_HEADER)))
                        .param("eventType", truncate(text(record, TYPE_HEADER), 100))
                        .param("key", record.key())
                        .param("payload", record.value())
                        .param("headers", json.writeValueAsString(headers(record)))
                        .param("exception", truncate(exception, 300))
                        .param("traceId", traceId(text(record, TRACEPARENT_HEADER)))
                        .param("deadLetteredAt", Timestamp.from(timestamp(record)))
                        .update()
                == 1;
    }

    /** DLT 메시지가 어느 소비자 그룹의 실패인가. Spring Kafka가 DLT로 보낼 때 적는다. */
    static @Nullable String originalGroup(ConsumerRecord<?, ?> record) {
        return text(record, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP);
    }

    private static List<Map<String, Object>> headers(ConsumerRecord<?, ?> record) {
        List<Map<String, Object>> headers = new ArrayList<>();
        for (Header header : record.headers()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", header.key());
            byte[] value = header.value();
            String text = value == null ? null : printable(value);
            entry.put("value", value == null ? null : text != null ? text : base64(value));
            entry.put("base64", value != null && text == null);
            headers.add(entry);
        }
        return headers;
    }

    private static @Nullable String text(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null ? null : utf8(header.value());
    }

    private static @Nullable Integer intHeader(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header != null && header.value() != null && header.value().length == Integer.BYTES
                ? ByteBuffer.wrap(header.value()).getInt()
                : null;
    }

    private static @Nullable Long longHeader(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header != null && header.value() != null && header.value().length == Long.BYTES
                ? ByteBuffer.wrap(header.value()).getLong()
                : null;
    }

    /** UTF-8로 읽을 수 있으면 문자열, 아니면 null. */
    private static @Nullable String utf8(byte[] value) {
        try {
            CharBuffer chars =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(value));
            return chars.toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /**
     * 문자열로 저장할 수 있는 헤더 값. 원래 파티션·오프셋 같은 바이너리 정수도 바이트로는 UTF-8이지만 NUL 같은 제어 문자가 들어 있고, jsonb는 {@code
     * \u0000}을 받지 않는다. 탭·줄바꿈(예외 스택) 말고 제어 문자가 있으면 null(→ base64)이다.
     */
    private static @Nullable String printable(byte[] value) {
        String text = utf8(value);
        if (text == null) {
            return null;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r') {
                return null;
            }
        }
        return text;
    }

    private static String base64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private static @Nullable UUID uuid(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable String traceId(@Nullable String traceparent) {
        if (traceparent == null) {
            return null;
        }
        var matcher = TRACEPARENT.matcher(traceparent);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static @Nullable String truncate(@Nullable String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String nonNull(@Nullable String group) {
        return group == null ? "" : group;
    }

    private static String stripSuffix(String topic) {
        return topic.endsWith(DLT_SUFFIX)
                ? topic.substring(0, topic.length() - DLT_SUFFIX.length())
                : topic;
    }

    private Instant timestamp(ConsumerRecord<?, ?> record) {
        return record.timestamp() >= 0 ? Instant.ofEpochMilli(record.timestamp()) : clock.instant();
    }
}
