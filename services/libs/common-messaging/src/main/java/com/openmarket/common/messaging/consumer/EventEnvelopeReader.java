package com.openmarket.common.messaging.consumer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kafka 메시지를 {@link EventEnvelope}로 바꾼다. 헤더 값은 UTF-8 문자열이고 메시지 값은 페이로드 JSON이다(Debezium Outbox Event
 * Router 설정, M0-d).
 *
 * <p>모르는 필드는 무시한다(Jackson 3 기본값). 발행자가 하위 호환으로 필드를 추가해도 소비자가 깨지지 않는다.
 */
public class EventEnvelopeReader {

    static final String ID = "id";
    static final String TYPE = "type";
    static final String OCCURRED_AT = "occurredAt";
    static final String SCHEMA_VERSION = "schemaVersion";
    static final String TRACEPARENT = "traceparent";

    private final JsonMapper jsonMapper;

    public EventEnvelopeReader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public <T> EventEnvelope<T> read(ConsumerRecord<String, String> record, Class<T> payloadType) {
        String type = required(record, TYPE);
        String key = record.key();
        if (key == null) {
            throw new InvalidEventException(describe(record) + " 키가 없다");
        }
        String value = record.value();
        if (value == null) {
            throw new InvalidEventException(describe(record) + " 값이 없다");
        }
        UUID id;
        Instant occurredAt;
        int schemaVersion;
        try {
            id = UUID.fromString(required(record, ID));
            occurredAt = Instant.parse(required(record, OCCURRED_AT));
            schemaVersion = Integer.parseInt(required(record, SCHEMA_VERSION));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidEventException(describe(record) + " 헤더 형식이 잘못됐다", e);
        }
        T payload;
        try {
            payload = jsonMapper.readValue(value, payloadType);
        } catch (JacksonException e) {
            // 원인 예외 메시지에 본문 일부가 들어갈 수 있어 붙이지 않는다.
            throw new InvalidEventException(
                    describe(record) + " 페이로드를 " + payloadType.getSimpleName() + "로 읽을 수 없다");
        }
        return new EventEnvelope<>(
                id, type, key, occurredAt, schemaVersion, header(record, TRACEPARENT), payload);
    }

    private static String required(ConsumerRecord<String, String> record, String name) {
        String value = header(record, name);
        if (value == null) {
            throw new InvalidEventException(describe(record) + " 헤더 " + name + "가 없다");
        }
        return value;
    }

    private static @Nullable String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private static String describe(ConsumerRecord<String, String> record) {
        return record.topic() + "-" + record.partition() + "@" + record.offset();
    }
}
