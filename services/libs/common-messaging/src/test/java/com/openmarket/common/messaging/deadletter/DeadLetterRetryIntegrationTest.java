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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.testcontainers.kafka.KafkaContainer;

/**
 * 두 그룹이 같은 토픽을 읽을 때 다시 보낸 메시지는 실패한 그룹만 처리한다(R14, ADR 0022 후속 작업). 다른 그룹의 처리 기록이 보관 기간(30일)이 지나 지워진
 * 뒤에도 그 그룹은 다시 처리하지 않는다.
 */
@SpringBootTest(
        properties = {
            "spring.application.name=catalog",
            // 다시 보내기가 감사 로그(common-web audit_log)를 남긴다.
            "spring.flyway.locations=classpath:db/common/messaging,classpath:db/common/web"
        })
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    DeadLetterRetryIntegrationTest.Listeners.class
})
class DeadLetterRetryIntegrationTest {

    static final String TOPIC = "dlt-retry.order";
    static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired KafkaContainer kafka;
    @Autowired JdbcClient jdbc;
    @Autowired DeadLetterService deadLetters;
    @Autowired TwoGroupListener listener;

    @Test
    @DisplayName("한 그룹만 실패해 DLT로 간 이벤트를 다시 보내면 그 그룹만 처리하고, 처리 기록이 지워진 다른 그룹은 다시 처리하지 않는다")
    void onlyTargetGroupProcessesRetriedMessage() throws Exception {
        UUID id = UUID.randomUUID();
        listener.catalogFails.set(true);

        send(id);
        await().atMost(TIMEOUT)
                .until(() -> listener.handled("promotion", id) == 1 && deadLetterId(id) != null);
        assertThat(listener.handled("catalog", id)).isZero();

        // promotion의 처리 기록이 보관 기간(30일)이 지나 지워졌다.
        jdbc.sql(
                        "DELETE FROM processed_event WHERE event_id = :id AND consumer = 'promotion.order-placed'")
                .param("id", id)
                .update();
        listener.catalogFails.set(false);

        deadLetters.retry(deadLetterId(id), "admin-1");

        await().atMost(TIMEOUT).until(() -> listener.handled("catalog", id) == 1);
        // promotion도 다시 보낸 메시지를 받지만 대상 그룹이 아니라 넘긴다.
        await().pollDelay(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(5))
                .until(() -> listener.handled("promotion", id) == 1);
    }

    private void send(UUID id) throws Exception {
        try (KafkaProducer<String, String> producer =
                new KafkaProducer<>(
                        Map.of(
                                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                kafka.getBootstrapServers()),
                        new StringSerializer(),
                        new StringSerializer())) {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(
                            TOPIC,
                            "order-" + id,
                            "{\"orderId\": \"order-1\", \"totalAmount\": 15000}");
            record.headers().add("id", utf8(id.toString()));
            record.headers().add("type", utf8("OrderPlaced"));
            record.headers().add("occurredAt", utf8(TestEvents.OCCURRED_AT));
            record.headers().add("schemaVersion", utf8("1"));
            producer.send(record).get();
        }
    }

    private UUID deadLetterId(UUID eventId) {
        return jdbc.sql("SELECT id FROM dead_letter WHERE event_id = :id")
                .param("id", eventId)
                .query(UUID.class)
                .optional()
                .orElse(null);
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Listeners {

        @Bean
        NewTopic retryTopic() {
            return new NewTopic(TOPIC, 3, (short) 1);
        }

        @Bean
        NewTopic retryDlt() {
            return new NewTopic(TOPIC + ".DLT", 3, (short) 1);
        }

        @Bean
        TwoGroupListener twoGroupListener(IdempotentEventProcessor processor) {
            return new TwoGroupListener(processor);
        }
    }

    /** 같은 토픽을 두 그룹이 읽는다. catalog는 실패하도록 정할 수 있다(영구 오류로 바로 DLT). */
    static class TwoGroupListener {

        final AtomicBoolean catalogFails = new AtomicBoolean();
        private final Map<String, Integer> handled = new ConcurrentHashMap<>();
        private final IdempotentEventProcessor processor;

        TwoGroupListener(IdempotentEventProcessor processor) {
            this.processor = processor;
        }

        @KafkaListener(topics = TOPIC, groupId = "catalog")
        void catalog(ConsumerRecord<String, String> record) {
            processor.process(
                    "catalog.order-placed",
                    record,
                    OrderPlacedMessage.class,
                    event -> {
                        if (catalogFails.get()) {
                            throw new InvalidEventException("catalog 처리 실패(테스트)");
                        }
                        handled.merge("catalog:" + event.id(), 1, Integer::sum);
                    });
        }

        @KafkaListener(topics = TOPIC, groupId = "promotion")
        void promotion(ConsumerRecord<String, String> record) {
            processor.process(
                    "promotion.order-placed",
                    record,
                    OrderPlacedMessage.class,
                    event -> handled.merge("promotion:" + event.id(), 1, Integer::sum));
        }

        int handled(String group, UUID id) {
            return handled.getOrDefault(group + ":" + id, 0);
        }
    }
}
