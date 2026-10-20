package com.openmarket.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.file.command.application.UploadFileCommand;
import com.openmarket.file.command.application.UploadFileUseCase;
import com.openmarket.file.infra.FileCleanupJob;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * catalog가 낸 상품 이벤트(Debezium과 같은 형식) → 리스너 → 첨부 확정·분리 → 정리. 페이로드는 발행자(catalog)의 JSON 예시를 그대로 쓰고 ID와
 * 이미지 목록만 바꾼다(docs/architecture/messaging.md "스키마 규칙").
 */
@SpringBootTest
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    ProductEventsIntegrationTest.Topics.class
})
class ProductEventsIntegrationTest {

    static final String TOPIC = "catalog.product";
    static final Path PUBLISHER_EXAMPLES = Path.of("../../core/catalog/src/test/resources/events");

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic catalogProduct() {
            return TopicBuilder.name(TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic catalogProductDlt() {
            return TopicBuilder.name(TOPIC + ".DLT").partitions(1).build();
        }
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) throws IOException {
        Path root = Files.createTempDirectory("file-storage");
        registry.add("openmarket.file.storage-root", root::toString);
    }

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired KafkaContainer kafka;

    @Autowired UploadFileUseCase upload;

    @Autowired FileCleanupJob cleanupJob;

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
    @DisplayName("등록하면 판매자가 올린 이미지만 붙고, 수정으로 빠진 이미지는 분리돼 7일 뒤 정리된다. 오래된 스냅샷은 되돌리지 않는다")
    void attachesDetachesAndCleansUp() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID first = uploadAs(sellerId);
        UUID second = uploadAs(sellerId);
        UUID foreign = uploadAs(UUID.randomUUID());

        UUID registered = UUID.randomUUID();
        send(
                registered,
                "ProductRegistered",
                snapshot("ProductRegistered.json", productId, sellerId, 1, first, second, foreign));
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(registered) == 1);
        assertThat(status(first)).isEqualTo("ATTACHED");
        assertThat(status(second)).isEqualTo("ATTACHED");
        assertThat(status(foreign)).isEqualTo("TEMP");

        UUID changed = UUID.randomUUID();
        send(
                changed,
                "ProductChanged",
                snapshot("ProductChanged.json", productId, sellerId, 2, second));
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(changed) == 1);
        assertThat(status(first)).isEqualTo("DETACHED");

        UUID stale = UUID.randomUUID();
        send(
                stale,
                "ProductPublished",
                snapshot("ProductPublished.json", productId, sellerId, 1, first, second));
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(stale) == 1);
        assertThat(status(first)).isEqualTo("DETACHED");

        jdbc.sql("UPDATE stored_file SET detached_at = :at WHERE id = :id")
                .param("at", Timestamp.from(Instant.now().minus(Duration.ofDays(8))))
                .param("id", first)
                .update();
        cleanupJob.cleanup();

        assertThat(count(first)).isZero();
        assertThat(status(second)).isEqualTo("ATTACHED");
    }

    /** 발행자 예시를 읽고 ID, revision, 이미지 목록만 바꾼다. 필드 구성은 예시 그대로다. */
    private String snapshot(
            String example, UUID productId, UUID sellerId, long revision, UUID... images)
            throws IOException {
        ObjectNode node =
                (ObjectNode) json.readTree(Files.readString(PUBLISHER_EXAMPLES.resolve(example)));
        node.put("productId", productId.toString());
        node.put("sellerId", sellerId.toString());
        node.put("revision", revision);
        ArrayNode ids = node.putArray("imageFileIds");
        for (UUID image : images) {
            ids.add(image.toString());
        }
        return json.writeValueAsString(node);
    }

    private UUID uploadAs(UUID uploader) throws IOException {
        byte[] content;
        try (InputStream in = getClass().getResourceAsStream("/images/landscape.jpg")) {
            content = Objects.requireNonNull(in).readAllBytes();
        }
        return upload.execute(
                        new UploadFileCommand(
                                uploader,
                                UploadFileCommand.Purpose.PRODUCT_IMAGE,
                                content.length,
                                () -> new ByteArrayInputStream(content)))
                .fileId();
    }

    private void send(UUID eventId, String type, String payload) throws Exception {
        String key = json.readTree(payload).get("productId").asString();
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, payload);
        record.headers().add("id", utf8(eventId.toString()));
        record.headers().add("type", utf8(type));
        record.headers().add("occurredAt", utf8("2026-10-20T03:04:05.123456Z"));
        record.headers().add("schemaVersion", utf8("1"));
        producer.send(record).get();
    }

    private String status(UUID fileId) {
        return jdbc.sql("SELECT status FROM stored_file WHERE id = :id")
                .param("id", fileId)
                .query(String.class)
                .single();
    }

    private int count(UUID fileId) {
        return jdbc.sql("SELECT count(*) FROM stored_file WHERE id = :id")
                .param("id", fileId)
                .query(Integer.class)
                .single();
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
