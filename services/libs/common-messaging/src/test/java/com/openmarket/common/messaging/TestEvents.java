package com.openmarket.common.messaging;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/** Debezium Outbox Event Router가 만드는 형식의 Kafka 메시지를 만든다. */
public final class TestEvents {

    public static final String TOPIC = "order.order";
    public static final String OCCURRED_AT = "2026-10-16T01:02:03.456Z";

    private TestEvents() {}

    public static ConsumerRecord<String, String> orderPlaced(UUID id, String orderId) {
        return record(id, orderId, "{\"orderId\": \"" + orderId + "\", \"totalAmount\": 15000}");
    }

    public static ConsumerRecord<String, String> record(UUID id, String key, String value) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 42L, key, value);
        header(record, "id", id.toString());
        header(record, "type", "OrderPlaced");
        header(record, "occurredAt", OCCURRED_AT);
        header(record, "schemaVersion", "1");
        return record;
    }

    public static void header(ConsumerRecord<String, String> record, String name, String value) {
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }

    public record OrderPlacedMessage(String orderId, long totalAmount) {}
}
