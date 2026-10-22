package com.openmarket.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회원 알림 이메일(docs/architecture/notification.md "이메일 받는 주소"): 원인 이벤트 → 인앱·이메일 알림 → 발송 작업이 member 내부
 * API(WireMock)로 주소 조회 → SMTP(Mailpit). member는 서비스 간 토큰으로 부른다.
 */
@SpringBootTest
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    NotificationTopics.class
})
class MemberEmailIntegrationTest {

    static final Path CORE = Path.of("../../core");
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final String TOKEN = "/oauth2/token";

    static final GenericContainer<?> MAILPIT =
            new GenericContainer<>(TestImages.MAILPIT).withExposedPorts(1025, 8025);

    static final WireMockServer MEMBER = new WireMockServer(options().dynamicPort());

    static {
        MAILPIT.start();
        MEMBER.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
        registry.add("openmarket.notification.member-url", MEMBER::baseUrl);
        registry.add(
                "spring.security.oauth2.client.provider.auth.token-uri",
                () -> MEMBER.baseUrl() + TOKEN);
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
        MEMBER.resetAll();
        MEMBER.stubFor(
                post(urlPathEqualTo(TOKEN))
                        .willReturn(
                                okJson(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "access_token", "service-token",
                                                        "token_type", "Bearer",
                                                        "expires_in", 3600,
                                                        "scope", "internal")))));
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    @DisplayName("주문 완료는 구매자의 인증한 주소로 문구와 주문 화면 링크를 보낸다. 주소는 저장하지 않고, 판매자 새 주문은 메일을 보내지 않는다")
    void sendsOrderPaidMail() throws Exception {
        JsonNode example = json.readTree(example("order", "OrderPaid"));
        String buyerId = example.get("buyerId").asString();
        String orderId = example.get("orderId").asString();
        contact(buyerId, 200, Map.of("email", "buyer@example.com", "emailVerified", true));
        UUID eventId = UUID.randomUUID();

        send("order.order", eventId, "OrderPaid", example.toString());

        await().atMost(Duration.ofSeconds(30)).until(() -> emailStatus(eventId).equals("SENT"));
        JsonNode messages = mailpitJson("/api/v1/messages");
        assertThat(messages.get("total").asInt()).isEqualTo(1);
        JsonNode summary = messages.get("messages").get(0);
        assertThat(summary.get("Subject").asString()).isEqualTo("[오픈 마켓] 주문이 완료됐습니다");
        assertThat(summary.get("To").get(0).get("Address").asString())
                .isEqualTo("buyer@example.com");
        JsonNode message = mailpitJson("/api/v1/message/" + summary.get("ID").asString());
        String link = "http://localhost:3000/orders/" + orderId;
        assertThat(message.get("Text").asString())
                .contains("주문이 완료됐습니다. 반팔 티셔츠 외 1건, 59,700원")
                .contains(link);
        assertThat(message.get("HTML").asString()).contains("href=\"" + link + "\"");

        MEMBER.verify(
                getRequestedFor(urlPathEqualTo("/internal/v1/members/" + buyerId + "/contact"))
                        .withHeader("Authorization", equalTo("Bearer service-token")));
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM notification WHERE source_event_id = :id"
                                                + " AND channel = 'EMAIL' AND recipient_email IS NULL")
                                .param("id", eventId)
                                .query(Integer.class)
                                .single())
                .as("판매자 새 주문은 인앱만, 구매자 메일은 주소를 저장하지 않는다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("member가 없는 회원(404)이라고 하면 보내지 않고 SKIPPED로 끝낸다")
    void skipsUnknownMember() throws Exception {
        String memberId =
                json.readTree(example("member", "SellerRejected")).get("memberId").asString();
        contact(memberId, 404, Map.of("code", "MEMBER_NOT_FOUND"));
        UUID eventId = UUID.randomUUID();

        send("member.seller", eventId, "SellerRejected", example("member", "SellerRejected"));

        await().atMost(Duration.ofSeconds(30)).until(() -> emailStatus(eventId).equals("SKIPPED"));
        assertThat(mailpitJson("/api/v1/messages").get("total").asInt()).isZero();
    }

    @Test
    @DisplayName("member가 503이면 보내지 않고 다시 시도할 차례를 기다린다")
    void retriesWhenMemberFails() throws Exception {
        String sellerId =
                json.readTree(example("catalog", "ProductBanned")).get("sellerId").asString();
        contact(sellerId, 503, Map.of());
        UUID eventId = UUID.randomUUID();

        send("catalog.product", eventId, "ProductBanned", example("catalog", "ProductBanned"));

        await().atMost(Duration.ofSeconds(30))
                .until(
                        () ->
                                jdbc.sql(
                                                        "SELECT attempts FROM notification WHERE source_event_id"
                                                                + " = :id AND channel = 'EMAIL'")
                                                .param("id", eventId)
                                                .query(Integer.class)
                                                .optional()
                                                .orElse(0)
                                        >= 1);
        assertThat(emailStatus(eventId)).isEqualTo("PENDING");
        assertThat(mailpitJson("/api/v1/messages").get("total").asInt()).isZero();
    }

    private void contact(String memberId, int status, Map<String, Object> body) {
        MEMBER.stubFor(
                get(urlPathEqualTo("/internal/v1/members/" + memberId + "/contact"))
                        .willReturn(
                                aResponse()
                                        .withStatus(status)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(json.writeValueAsString(body))));
    }

    private String emailStatus(UUID eventId) {
        return jdbc.sql(
                        "SELECT status FROM notification WHERE source_event_id = :id AND channel ="
                                + " 'EMAIL'")
                .param("id", eventId)
                .query(String.class)
                .optional()
                .orElse("NONE");
    }

    private static String example(String publisher, String type) throws Exception {
        return Files.readString(
                CORE.resolve(publisher).resolve("src/test/resources/events/" + type + ".json"));
    }

    private void send(String topic, UUID eventId, String type, String payload) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, "key", payload);
        record.headers().add("id", utf8(eventId.toString()));
        record.headers().add("type", utf8(type));
        record.headers().add("occurredAt", utf8("2026-10-22T03:04:05.123456Z"));
        record.headers().add("schemaVersion", utf8("1"));
        producer.send(record).get();
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
