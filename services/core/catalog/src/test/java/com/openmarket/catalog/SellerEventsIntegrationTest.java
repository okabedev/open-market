package com.openmarket.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
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
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * member가 낸 판매자 이벤트(Debezium과 같은 형식) → 리스너 → 판매자 사본. 페이로드는 발행자(member)의 JSON 예시를 그대로 쓴다
 * (docs/architecture/messaging.md "스키마 규칙").
 */
@SpringBootTest
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    SellerEventsIntegrationTest.Topics.class
})
class SellerEventsIntegrationTest {

    static final String TOPIC = "member.seller";
    static final Path PUBLISHER_EXAMPLES = Path.of("../member/src/test/resources/events");

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic memberSeller() {
            return TopicBuilder.name(TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic memberSellerDlt() {
            return TopicBuilder.name(TOPIC + ".DLT").partitions(1).build();
        }
    }

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired KafkaContainer kafka;

    KafkaProducer<String, String> producer;

    @BeforeEach
    void setUp() {
        producer =
                new KafkaProducer<>(
                        Map.of(
                                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                kafka.getBootstrapServers()),
                        new StringSerializer(),
                        new StringSerializer());
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    @DisplayName("SellerApproved가 두 번 와도 판매자 사본은 하나이고 상호가 들어간다. 다른 판매자 이벤트는 건너뛴다")
    void createsSellerCopyOnce() throws Exception {
        String approved = Files.readString(PUBLISHER_EXAMPLES.resolve("SellerApproved.json"));
        String sellerId = json.readTree(approved).get("sellerId").asString();
        UUID approvedEvent = UUID.randomUUID();
        UUID otherEvent = UUID.randomUUID();

        send(
                otherEvent,
                "SellerRejected",
                Files.readString(PUBLISHER_EXAMPLES.resolve("SellerRejected.json")),
                sellerId);
        send(approvedEvent, "SellerApproved", approved, sellerId);
        send(approvedEvent, "SellerApproved", approved, sellerId);

        await().atMost(Duration.ofSeconds(30)).until(() -> processed(approvedEvent) == 1);
        Map<String, Object> seller =
                jdbc.sql("SELECT store_name, status FROM seller WHERE id = :id")
                        .param("id", UUID.fromString(sellerId))
                        .query()
                        .singleRow();
        assertThat(seller)
                .containsEntry("store_name", json.readTree(approved).get("storeName").asString())
                .containsEntry("status", "ACTIVE");
        assertThat(processed(otherEvent)).isZero();
    }

    private void send(UUID eventId, String type, String payload, String key) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, payload);
        record.headers().add("id", utf8(eventId.toString()));
        record.headers().add("type", utf8(type));
        record.headers().add("occurredAt", utf8("2026-10-19T03:04:05.123456Z"));
        record.headers().add("schemaVersion", utf8("1"));
        producer.send(record).get();
    }

    private int processed(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM processed_event WHERE event_id = :id")
                .param("id", eventId)
                .query(Integer.class)
                .single();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
