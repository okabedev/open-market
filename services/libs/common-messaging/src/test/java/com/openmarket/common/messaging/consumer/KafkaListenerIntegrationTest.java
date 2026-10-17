package com.openmarket.common.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.common.messaging.TestEvents;
import com.openmarket.common.messaging.TestEvents.OrderPlacedMessage;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.testcontainers.kafka.KafkaContainer;

/** 브로커를 거쳐 리스너 → 멱등 소비 → 재시도·DLT까지 확인한다. 메시지는 Debezium Outbox Event Router와 같은 형식으로 보낸다. */
@SpringBootTest
@AutoConfigureTracing
@ExtendWith(OutputCaptureExtension.class)
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    KafkaListenerIntegrationTest.Listeners.class
})
class KafkaListenerIntegrationTest {

    static final String TOPIC = TestEvents.TOPIC;
    static final String DLT = TOPIC + ".DLT";
    static final String LISTENER = "catalog.order-placed";
    static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired KafkaContainer kafka;

    @Autowired TestListener listener;

    @Autowired JdbcClient jdbc;

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
    @DisplayName("같은 이벤트가 두 번 오면 핸들러는 한 번만 실행되고 처리 기록은 한 행이다")
    void duplicateIsHandledOnce() throws Exception {
        UUID id = UUID.randomUUID();
        String key = newKey();

        send(id, key, orderPlaced(key), null);
        send(id, key, orderPlaced(key), null);

        await().atMost(TIMEOUT).until(() -> listener.deliveries(key) == 2);
        assertThat(listener.handled(key)).isEqualTo(1);
        assertThat(processedCount(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("일시 오류는 재시도해서 처리하고 DLT로 보내지 않는다")
    void transientFailureIsRetried() throws Exception {
        UUID id = UUID.randomUUID();
        String key = newKey();
        listener.failTimes(key, 2);

        send(id, key, orderPlaced(key), null);

        await().atMost(TIMEOUT).until(() -> listener.handled(key) == 1);
        assertThat(listener.deliveries(key)).isEqualTo(3);
        assertThat(processedCount(id)).isEqualTo(1);
        assertThat(listener.deadLetter(key)).isNull();
    }

    @Test
    @DisplayName("재시도 3회 뒤에도 실패하면 원본 그대로 같은 파티션의 DLT로 보내고 처리 기록은 남기지 않는다")
    void exhaustedRetriesGoToDeadLetterTopic(CapturedOutput output) throws Exception {
        UUID id = UUID.randomUUID();
        String key = newKey();
        listener.failTimes(key, Integer.MAX_VALUE);

        RecordMetadata sent = send(id, key, orderPlaced(key), null);

        await().atMost(TIMEOUT).until(() -> listener.deadLetter(key) != null);
        ConsumerRecord<String, String> dead = listener.deadLetter(key);
        assertThat(listener.deliveries(key)).isEqualTo(4);
        assertThat(dead.value()).isEqualTo(orderPlaced(key));
        assertThat(dead.partition()).isEqualTo(sent.partition());
        assertThat(header(dead, "id")).isEqualTo(id.toString());
        assertThat(header(dead, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .isEqualTo(IllegalStateException.class.getName());
        assertThat(processedCount(id)).isZero();
        // 로그에는 위치와 이벤트 ID만 남고 키·값은 남지 않는다.
        assertThat(output.getOut())
                .contains("이벤트를 DLT로 보냈다: " + TOPIC + "-" + sent.partition() + "@")
                .contains(id.toString())
                .doesNotContain(key);
    }

    @Test
    @DisplayName("읽을 수 없는 메시지는 재시도하지 않고 바로 DLT로 보낸다")
    void invalidEventGoesToDeadLetterTopicWithoutRetry() throws Exception {
        UUID id = UUID.randomUUID();
        String key = newKey();

        send(id, key, "not json", null);

        await().atMost(TIMEOUT).until(() -> listener.deadLetter(key) != null);
        assertThat(listener.deliveries(key)).isEqualTo(1);
        assertThat(listener.handled(key)).isZero();
        assertThat(header(listener.deadLetter(key), KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .isEqualTo(InvalidEventException.class.getName());
    }

    @Test
    @DisplayName("traceparent 헤더의 트레이스를 이어 받고, 핸들러가 쌓은 outbox 이벤트에도 같은 트레이스가 들어간다")
    void continuesTraceFromHeader() throws Exception {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        UUID id = UUID.randomUUID();
        String key = newKey();

        send(id, key, orderPlaced(key), "00-" + traceId + "-00f067aa0ba902b7-01");

        await().atMost(TIMEOUT).until(() -> listener.handled(key) == 1);
        assertThat(listener.traceId(key)).isEqualTo(traceId);
        assertThat(outboxTraceparent(listener.appended(key))).startsWith("00-" + traceId + "-");
    }

    @Test
    @DisplayName("traceparent 헤더 값이 비어 있으면 새 트레이스로 처리한다")
    void handlesEmptyTraceparent() throws Exception {
        UUID id = UUID.randomUUID();
        String key = newKey();

        send(id, key, orderPlaced(key), null, true);

        await().atMost(TIMEOUT).until(() -> listener.handled(key) == 1);
        assertThat(listener.traceId(key)).isNotBlank();
    }

    private RecordMetadata send(UUID id, String key, String value, @Nullable String traceparent)
            throws Exception {
        return send(id, key, value, traceparent, false);
    }

    /** Debezium처럼 헤더를 UTF-8 문자열로 넣는다. traceparent 컬럼이 비어 있던 행은 값이 null인 헤더가 된다. */
    private RecordMetadata send(
            UUID id,
            String key,
            String value,
            @Nullable String traceparent,
            boolean nullTraceparentHeader)
            throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, value);
        record.headers().add("id", utf8(id.toString()));
        record.headers().add("type", utf8("OrderPlaced"));
        record.headers().add("occurredAt", utf8(TestEvents.OCCURRED_AT));
        record.headers().add("schemaVersion", utf8("1"));
        if (traceparent != null) {
            record.headers().add("traceparent", utf8(traceparent));
        } else if (nullTraceparentHeader) {
            record.headers().add("traceparent", null);
        }
        return producer.send(record).get();
    }

    private static String orderPlaced(String orderId) {
        return "{\"orderId\": \"" + orderId + "\", \"totalAmount\": 15000}";
    }

    private static String newKey() {
        return "order-" + UUID.randomUUID();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static @Nullable String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private int processedCount(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM processed_event WHERE event_id = :id")
                .param("id", eventId)
                .query(Integer.class)
                .single();
    }

    private @Nullable String outboxTraceparent(UUID id) {
        return jdbc.sql("SELECT traceparent FROM outbox_event WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Listeners {

        @Bean
        NewTopic orderTopic() {
            return new NewTopic(TOPIC, 3, (short) 1);
        }

        @Bean
        NewTopic orderDeadLetterTopic() {
            return new NewTopic(DLT, 3, (short) 1);
        }

        @Bean
        TestListener testListener(
                IdempotentEventProcessor processor, Outbox outbox, Tracer tracer) {
            return new TestListener(processor, outbox, tracer);
        }
    }

    /** 서비스의 {@code ui.event} 리스너와 같은 모양이다. 키(주문 ID)별로 전달 횟수와 처리 결과를 남긴다. */
    static class TestListener {

        private final IdempotentEventProcessor processor;
        private final Outbox outbox;
        private final Tracer tracer;

        private final Map<String, AtomicInteger> deliveries = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> handled = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();
        private final Map<String, String> traceIds = new ConcurrentHashMap<>();
        private final Map<String, UUID> appended = new ConcurrentHashMap<>();
        private final Map<String, ConsumerRecord<String, String>> deadLetters =
                new ConcurrentHashMap<>();

        TestListener(IdempotentEventProcessor processor, Outbox outbox, Tracer tracer) {
            this.processor = processor;
            this.outbox = outbox;
            this.tracer = tracer;
        }

        @KafkaListener(topics = TOPIC, groupId = "catalog")
        void on(ConsumerRecord<String, String> record) {
            counter(deliveries, record.key()).incrementAndGet();
            processor.process(LISTENER, record, OrderPlacedMessage.class, this::handle);
        }

        @KafkaListener(topics = DLT, groupId = "test-dlt")
        void onDeadLetter(ConsumerRecord<String, String> record) {
            deadLetters.put(record.key(), record);
        }

        private void handle(EventEnvelope<OrderPlacedMessage> event) {
            String key = event.aggregateId();
            if (counter(failures, key).getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
                throw new IllegalStateException("일시 장애");
            }
            Span span = tracer.currentSpan();
            if (span != null) {
                traceIds.put(key, span.context().traceId());
            }
            appended.put(
                    key, outbox.append("catalog.stock", key, "StockReserved", event.payload()));
            counter(handled, key).incrementAndGet();
        }

        void failTimes(String key, int times) {
            counter(failures, key).set(times);
        }

        int deliveries(String key) {
            return counter(deliveries, key).get();
        }

        int handled(String key) {
            return counter(handled, key).get();
        }

        @Nullable String traceId(String key) {
            return traceIds.get(key);
        }

        UUID appended(String key) {
            return appended.get(key);
        }

        @Nullable ConsumerRecord<String, String> deadLetter(String key) {
            return deadLetters.get(key);
        }

        private static AtomicInteger counter(Map<String, AtomicInteger> map, String key) {
            return map.computeIfAbsent(key, k -> new AtomicInteger());
        }
    }
}
