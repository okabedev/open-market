package com.openmarket.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.test.container.TestImages;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 인증 메일 왕복: auth가 낸 {@code EmailVerificationRequested}(Debezium과 같은 형식) → 리스너 → 알림 저장 → 발송 작업 →
 * SMTP(Mailpit). 페이로드는 발행자(auth)의 JSON 예시를 그대로 쓴다(docs/architecture/messaging.md "스키마 규칙").
 */
@SpringBootTest
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    EmailVerificationMailIntegrationTest.Topics.class
})
class EmailVerificationMailIntegrationTest {

    static final String TOPIC = "auth.account";
    static final Path PUBLISHER_EXAMPLES = Path.of("../auth/src/test/resources/events");
    static final HttpClient HTTP = HttpClient.newHttpClient();

    static final GenericContainer<?> MAILPIT =
            new GenericContainer<>(TestImages.MAILPIT).withExposedPorts(1025, 8025);

    static {
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic authAccount() {
            return TopicBuilder.name(TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic authAccountDlt() {
            return TopicBuilder.name(TOPIC + ".DLT").partitions(1).build();
        }
    }

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired KafkaContainer kafka;

    KafkaProducer<String, String> producer;

    @BeforeEach
    void setUp() throws Exception {
        producer =
                new KafkaProducer<>(
                        Map.of(
                                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                kafka.getBootstrapServers()),
                        new StringSerializer(),
                        new StringSerializer());
        HTTP.send(
                HttpRequest.newBuilder(mailpit("/api/v1/messages")).DELETE().build(),
                HttpResponse.BodyHandlers.discarding());
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    @DisplayName("EmailVerificationRequested가 두 번 와도 인증 메일은 한 통만 간다. 같은 토픽의 다른 이벤트는 건너뛴다")
    void sendsVerificationMailOnce() throws Exception {
        JsonNode example =
                json.readTree(
                        Files.readString(
                                PUBLISHER_EXAMPLES.resolve("EmailVerificationRequested.json")));
        String accountId = example.get("accountId").asString();
        UUID eventId = UUID.randomUUID();
        UUID otherEventId = UUID.randomUUID();

        send(eventId, "EmailVerificationRequested", example.toString(), accountId);
        send(eventId, "EmailVerificationRequested", example.toString(), accountId);
        send(
                otherEventId,
                "AccountRegistered",
                Files.readString(PUBLISHER_EXAMPLES.resolve("AccountRegistered.json")),
                accountId);

        await().atMost(Duration.ofSeconds(30)).until(() -> status(eventId).equals("SENT"));
        JsonNode messages = mailpitJson("/api/v1/messages");
        assertThat(messages.get("total").asInt()).isEqualTo(1);
        JsonNode summary = messages.get("messages").get(0);
        assertThat(summary.get("Subject").asString()).isEqualTo("[오픈 마켓] 이메일 인증을 완료해 주세요");
        assertThat(summary.get("To").get(0).get("Address").asString())
                .isEqualTo(example.get("email").asString());

        JsonNode message = mailpitJson("/api/v1/message/" + summary.get("ID").asString());
        String url = example.get("verificationUrl").asString();
        assertThat(message.get("Text").asString()).contains(url).contains("2026년 10월 19일 12:04");
        assertThat(message.get("HTML").asString()).contains("href=\"" + url + "\"");

        assertThat(count("SELECT count(*) FROM notification WHERE source_event_id = :id", eventId))
                .isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM notification_param p JOIN notification n ON n.id = p.notification_id WHERE n.source_event_id = :id",
                                eventId))
                .as("보낸 뒤에는 인증 링크를 지운다")
                .isZero();
        assertThat(count("SELECT count(*) FROM processed_event WHERE event_id = :id", otherEventId))
                .isZero();
    }

    private void send(UUID eventId, String type, String payload, String key) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, payload);
        record.headers().add("id", utf8(eventId.toString()));
        record.headers().add("type", utf8(type));
        record.headers().add("occurredAt", utf8("2026-10-18T03:04:05.123456Z"));
        record.headers().add("schemaVersion", utf8("1"));
        producer.send(record).get();
    }

    private String status(UUID eventId) {
        return jdbc.sql("SELECT status FROM notification WHERE source_event_id = :id")
                .param("id", eventId)
                .query(String.class)
                .optional()
                .orElse("NONE");
    }

    private int count(String sql, UUID id) {
        return jdbc.sql(sql).param("id", id).query(Integer.class).single();
    }

    private JsonNode mailpitJson(String path) throws Exception {
        return json.readTree(
                HTTP.send(
                                HttpRequest.newBuilder(mailpit(path)).build(),
                                HttpResponse.BodyHandlers.ofString())
                        .body());
    }

    private static URI mailpit(String path) {
        return URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + path);
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
