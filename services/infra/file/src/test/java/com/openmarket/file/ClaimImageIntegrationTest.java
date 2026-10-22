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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 클레임 사진: 회원 업로드 → fulfillment가 낸 {@code ClaimRequested}(Debezium과 같은 형식)로 첨부 → 비공개
 * 제공(docs/architecture/file.md "클레임 사진", "비공개 파일"). 페이로드는 발행자(fulfillment)의 JSON 예시를 읽어 ID와 사진만
 * 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    ClaimImageIntegrationTest.Topics.class
})
class ClaimImageIntegrationTest {

    static final String TOPIC = "fulfillment.claim";
    static final Path PUBLISHER_EXAMPLES =
            Path.of("../../core/fulfillment/src/test/resources/events");

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic fulfillmentClaim() {
            return TopicBuilder.name(TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic fulfillmentClaimDlt() {
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
    @DisplayName("구매자가 올린 클레임 사진은 비공개다. 붙기 전에는 올린 사람만, 클레임에 붙으면 판매자와 관리자도 받는다")
    void uploadsAttachesAndServesPrivately() throws Exception {
        String body =
                mockMvc.perform(
                                multipart("/v1/files")
                                        .file(image())
                                        .param("purpose", "CLAIM_IMAGE")
                                        .with(FileTokens.buyer(buyer)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.variants[0].name").value("w300"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID fileId = UUID.fromString(json.readTree(body).get("fileId").asString());
        assertThat(json.readTree(body).get("variants").get(1).get("url").asString())
                .isEqualTo("/api/file/v1/files/" + fileId + "/w800");

        expectImage(fileId, FileTokens.buyer(buyer));
        expectHidden(fileId, FileTokens.seller(seller));
        mockMvc.perform(get("/v1/admin/files/{id}/w300", fileId).with(admin()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/files/{id}/w300", fileId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));

        UUID claimId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        send(eventId, claimId, fileId);
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.sql(
                                                                "SELECT status || '/' || coalesce(owner_type, '-')"
                                                                        + " FROM stored_file WHERE id"
                                                                        + " = :id")
                                                        .param("id", fileId)
                                                        .query(String.class)
                                                        .single())
                                        .isEqualTo("ATTACHED/CLAIM"));

        expectImage(fileId, FileTokens.seller(seller));
        expectImage(fileId, FileTokens.buyer(buyer));
        expectHidden(fileId, FileTokens.seller(UUID.randomUUID()));
        mockMvc.perform(get("/v1/admin/files/{id}/w300", fileId).with(admin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/admin/files/{id}/w300", fileId).with(FileTokens.buyer(buyer)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("경로마다 받는 용도가 정해져 있다. 회원 경로에 상품 이미지, 판매자 경로에 클레임 사진은 400이다")
    void purposePerPath() throws Exception {
        mockMvc.perform(
                        multipart("/v1/files")
                                .file(image())
                                .param("purpose", "PRODUCT_IMAGE")
                                .with(FileTokens.seller(seller)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
        mockMvc.perform(
                        multipart("/v1/seller/files")
                                .file(image())
                                .param("purpose", "CLAIM_IMAGE")
                                .with(FileTokens.seller(seller)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
        mockMvc.perform(multipart("/v1/files").file(image()).param("purpose", "CLAIM_IMAGE"))
                .andExpect(status().isUnauthorized());
    }

    private void expectImage(UUID fileId, RequestPostProcessor viewer) throws Exception {
        mockMvc.perform(get("/v1/files/{id}/w300", fileId).with(viewer))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", "max-age=86400, private"));
    }

    private void expectHidden(UUID fileId, RequestPostProcessor viewer) throws Exception {
        mockMvc.perform(get("/v1/files/{id}/w300", fileId).with(viewer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
    }

    private static RequestPostProcessor admin() {
        return FileTokens.admin(UUID.randomUUID());
    }

    private void send(UUID eventId, UUID claimId, UUID fileId) throws Exception {
        ObjectNode payload =
                (ObjectNode)
                        json.readTree(
                                Files.readString(
                                        PUBLISHER_EXAMPLES.resolve("ClaimRequested.json")));
        payload.put("claimId", claimId.toString());
        payload.put("type", "RETURN");
        payload.put("buyerId", buyer.toString());
        payload.put("sellerId", seller.toString());
        payload.put("reason", "DEFECTIVE");
        payload.put("fault", "SELLER");
        payload.putArray("fileIds").add(fileId.toString());
        ProducerRecord<String, String> record =
                new ProducerRecord<>(
                        TOPIC,
                        payload.get("sellerOrderId").asString(),
                        json.writeValueAsString(payload));
        record.headers().add("id", eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("type", "ClaimRequested".getBytes(StandardCharsets.UTF_8));
        record.headers().add("occurredAt", "2026-10-22T00:00:00Z".getBytes(StandardCharsets.UTF_8));
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
