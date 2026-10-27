package com.openmarket.common.messaging.deadletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.common.messaging.TestEvents;
import com.openmarket.common.messaging.TestEvents.OrderPlacedMessage;
import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.common.messaging.consumer.InvalidEventException;
import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.testcontainers.kafka.KafkaContainer;

/**
 * DLT 수집기(ADR 0022, docs/architecture/messaging.md "DLT 재처리"). 서비스 이름이 catalog인 앱에서, 리스너 그룹
 * catalog가 처리하지 못해 DLT로 간 메시지가 dead_letter에 모이고, 다른 서비스 그룹의 DLT 메시지는 넘기는지 본다.
 */
@SpringBootTest(properties = "spring.application.name=catalog")
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    DeadLetterCollectorIntegrationTest.Listeners.class
})
class DeadLetterCollectorIntegrationTest {

    static final String TOPIC = "dlt-test.order";
    static final String DLT = TOPIC + ".DLT";
    static final Duration TIMEOUT = Duration.ofSeconds(30);
    static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Autowired KafkaContainer kafka;
    @Autowired JdbcClient jdbc;
    @Autowired DeadLetterRecorder recorder;

    KafkaProducer<String, String> producer;

    @BeforeEach
    void createProducer() {
        producer =
                new KafkaProducer<>(
                        Map.of(
                                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                kafka.getBootstrapServers()),
                        new StringSerializer(),
                        new StringSerializer());
    }

    @AfterEach
    void closeProducer() {
        producer.close();
    }

    @Test
    @DisplayName("이 서비스의 리스너 그룹이 처리하지 못해 DLT로 간 메시지를 원래 위치·이벤트·예외·trace id와 함께 PENDING으로 모은다")
    void collectsOwnGroupDeadLetter() throws Exception {
        UUID id = UUID.randomUUID();
        String key = "order-" + UUID.randomUUID();

        RecordMetadata sent = send(TOPIC, id, key, "not json", Map.of());

        await().atMost(TIMEOUT).until(() -> rows(id) == 1);
        Map<String, Object> row =
                jdbc.sql("SELECT * FROM dead_letter WHERE event_id = :id")
                        .param("id", id)
                        .query()
                        .singleRow();
        assertThat(row)
                .containsEntry("consumer_group", "catalog")
                .containsEntry("dlt_topic", DLT)
                .containsEntry("dlt_partition", sent.partition())
                .containsEntry("original_topic", TOPIC)
                .containsEntry("original_partition", sent.partition())
                .containsEntry("original_offset", sent.offset())
                .containsEntry("event_type", "OrderPlaced")
                .containsEntry("message_key", key)
                .containsEntry("payload", "not json")
                .containsEntry("exception_class", InvalidEventException.class.getName())
                .containsEntry("trace_id", TRACE_ID)
                .containsEntry("status", "PENDING");
        assertThat(row.get("dead_lettered_at")).isNotNull();
        // 헤더는 [{key, value, base64}] 목록이다. 다시 보낼 때 원래 헤더를 그대로 되살린다.
        assertThat(String.valueOf(row.get("headers")))
                .contains("\"key\": \"id\"")
                .contains(id.toString())
                .contains(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP);
    }

    @Test
    @DisplayName("다른 서비스 그룹이 실패한 DLT 메시지는 모으지 않는다")
    void skipsOtherGroupsDeadLetter() throws Exception {
        UUID other = UUID.randomUUID();
        UUID mine = UUID.randomUUID();

        send(
                DLT,
                other,
                "order-a",
                "{}",
                Map.of(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, "settlement"));
        send(
                DLT,
                mine,
                "order-b",
                "{}",
                Map.of(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, "catalog"));

        // 같은 파티션이 아닐 수 있어 뒤 메시지만으로 앞 메시지의 처리를 알 수 없다. 둘을 모두 지난 시점을 잠깐 더 기다린다.
        await().atMost(TIMEOUT).until(() -> rows(mine) == 1);
        await().during(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(5))
                .until(() -> rows(other) == 0);
    }

    @Test
    @DisplayName("같은 DLT 메시지를 다시 읽어도 한 행이다")
    void sameDeadLetterIsRecordedOnce() {
        UUID id = UUID.randomUUID();
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(DLT, 0, 12_345L, "order-c", "{}");
        record.headers().add("id", utf8(id.toString()));
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, utf8("catalog"));

        recorder.record(record);
        recorder.record(record);

        assertThat(rows(id)).isEqualTo(1);
    }

    private RecordMetadata send(
            String topic, UUID id, String key, String value, Map<String, String> extraHeaders)
            throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        record.headers().add("id", utf8(id.toString()));
        record.headers().add("type", utf8("OrderPlaced"));
        record.headers().add("occurredAt", utf8(TestEvents.OCCURRED_AT));
        record.headers().add("schemaVersion", utf8("1"));
        record.headers().add("traceparent", utf8("00-" + TRACE_ID + "-00f067aa0ba902b7-01"));
        extraHeaders.forEach((name, value2) -> record.headers().add(name, utf8(value2)));
        return producer.send(record).get();
    }

    private int rows(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM dead_letter WHERE event_id = :id")
                .param("id", eventId)
                .query(Integer.class)
                .single();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Listeners {

        @Bean
        NewTopic deadLetterTestTopic() {
            return new NewTopic(TOPIC, 3, (short) 1);
        }

        @Bean
        NewTopic deadLetterTestDlt() {
            return new NewTopic(DLT, 3, (short) 1);
        }

        @Bean
        CatalogListener catalogListener(IdempotentEventProcessor processor) {
            return new CatalogListener(processor);
        }
    }

    /** 서비스의 리스너와 같은 모양이다. 읽을 수 없는 메시지는 처리기가 영구 오류로 바로 DLT에 보낸다. */
    static class CatalogListener {

        private final IdempotentEventProcessor processor;

        CatalogListener(IdempotentEventProcessor processor) {
            this.processor = processor;
        }

        @KafkaListener(topics = TOPIC, groupId = "catalog")
        void on(ConsumerRecord<String, String> record) {
            processor.process(
                    "catalog.order-placed", record, OrderPlacedMessage.class, event -> {});
        }
    }
}
