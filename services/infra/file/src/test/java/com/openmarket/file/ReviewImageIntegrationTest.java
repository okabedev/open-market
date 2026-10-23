package com.openmarket.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 리뷰 사진: 회원 업로드 → review가 낸 {@code ReviewCreated}(Debezium과 같은 형식)로 첨부 → 공개
 * 제공(docs/architecture/file.md "리뷰 사진"). 페이로드는 발행자(review)의 JSON 예시를 읽어 ID와 사진만 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    ReviewImageIntegrationTest.Topics.class
})
class ReviewImageIntegrationTest {

    static final String TOPIC = "review.review";
    static final Path PUBLISHER_EXAMPLES = Path.of("../../core/review/src/test/resources/events");

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic reviewReview() {
            return TopicBuilder.name(TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic reviewReviewDlt() {
            return TopicBuilder.name(TOPIC + ".DLT").partitions(1).build();
        }
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) throws IOException {
        Path root = Files.createTempDirectory("file-storage");
        registry.add("openmarket.file.storage-root", root::toString);
    }

    @Autowired MockMvc mockMvc;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired KafkaContainer kafka;

    KafkaProducer<String, String> producer;

    final UUID buyer = UUID.randomUUID();
    final UUID seller = UUID.randomUUID();

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
    @DisplayName("회원이 올린 리뷰 사진은 공개다. 변형 URL은 토큰 없이 받는 경로이고, ReviewCreated로 리뷰에 붙는다")
    void uploadsAttachesAndServesPublicly() throws Exception {
        String body =
                mockMvc.perform(
                                multipart("/v1/files")
                                        .file(image())
                                        .param("purpose", "REVIEW_IMAGE")
                                        .with(FileTokens.buyer(buyer)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.variants[0].name").value("w300"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID fileId = UUID.fromString(json.readTree(body).get("fileId").asString());
        assertThat(json.readTree(body).get("variants").get(1).get("url").asString())
                .isEqualTo("/files/" + fileId + "/w800");
        mockMvc.perform(get("/files/{id}/w300", fileId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));

        UUID reviewId = UUID.randomUUID();
        send(UUID.randomUUID(), reviewId, fileId);
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.sql(
                                                                "SELECT status || '/' ||"
                                                                        + " coalesce(owner_type, '-')"
                                                                        + " || '/' ||"
                                                                        + " coalesce(owner_id::text,"
                                                                        + " '-') FROM stored_file"
                                                                        + " WHERE id = :id")
                                                        .param("id", fileId)
                                                        .query(String.class)
                                                        // single()의 예외는 단언 실패가 아니라서 다시 시도되지 않는다.
                                                        .list())
                                        .containsExactly("ATTACHED/REVIEW/" + reviewId));
        assertThat(
                        jdbc.sql("SELECT seller_id FROM file_owner WHERE owner_id = :id")
                                .param("id", reviewId)
                                .query(UUID.class)
                                .single())
                .isEqualTo(seller);
    }

    @Test
    @DisplayName("판매자 경로는 리뷰 사진을 받지 않는다")
    void sellerPathRejectsReviewImage() throws Exception {
        mockMvc.perform(
                        multipart("/v1/seller/files")
                                .file(image())
                                .param("purpose", "REVIEW_IMAGE")
                                .with(FileTokens.seller(seller)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    private void send(UUID eventId, UUID reviewId, UUID fileId) throws Exception {
        ObjectNode payload =
                (ObjectNode)
                        json.readTree(
                                Files.readString(PUBLISHER_EXAMPLES.resolve("ReviewCreated.json")));
        payload.put("reviewId", reviewId.toString());
        payload.put("buyerId", buyer.toString());
        payload.put("sellerId", seller.toString());
        payload.putArray("fileIds").add(fileId.toString());
        ProducerRecord<String, String> record =
                new ProducerRecord<>(TOPIC, reviewId.toString(), json.writeValueAsString(payload));
        record.headers().add("id", eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("type", "ReviewCreated".getBytes(StandardCharsets.UTF_8));
        record.headers().add("occurredAt", "2026-10-29T01:00:00Z".getBytes(StandardCharsets.UTF_8));
        record.headers().add("schemaVersion", "1".getBytes(StandardCharsets.UTF_8));
        producer.send(record).get();
    }

    private MockMultipartFile image() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/images/landscape.jpg")) {
            return new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", Objects.requireNonNull(in).readAllBytes());
        }
    }
}
