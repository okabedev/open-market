package com.openmarket.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * M0 샘플 이벤트 왕복. 발행은 API → outbox 행까지, 소비는 Debezium과 같은 형식의 메시지 → 리스너 → 처리 기록까지 확인한다. Debezium 구간은
 * Compose E2E(docs/roadmap.md M0 완료 기준)에서 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTracing
@ExtendWith(OutputCaptureExtension.class)
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    PingEventIntegrationTest.Topics.class
})
class PingEventIntegrationTest {

    static final String TOPIC = "member.ping";
    static final String LISTENER = "member.ping-sent";
    static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired MockMvc mockMvc;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper jsonMapper;

    @Autowired KafkaContainer kafka;

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
    @DisplayName("샘플 API는 PingSent를 member.ping 토픽으로 outbox에 쌓고, 요청의 트레이스를 함께 남긴다")
    void sendPingAppendsEventToOutbox() throws Exception {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";

        String body =
                mockMvc.perform(
                                MockMvcRequestBuilders.post("/v1/ping")
                                        .header(
                                                "traceparent",
                                                "00-" + traceId + "-00f067aa0ba902b7-01"))
                        .andExpect(status().isAccepted())
                        .andExpect(jsonPath("$.pingId").exists())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String pingId = jsonMapper.readTree(body).get("pingId").asString();

        Map<String, Object> row =
                jdbc.sql(
                                "SELECT aggregate_type, type, schema_version, payload::text AS"
                                        + " payload, traceparent FROM outbox_event WHERE"
                                        + " aggregate_id = :pingId")
                        .param("pingId", pingId)
                        .query()
                        .singleRow();
        assertThat(row)
                .containsEntry("aggregate_type", TOPIC)
                .containsEntry("type", "PingSent")
                .containsEntry("schema_version", 1);
        JsonNode payload = jsonMapper.readTree((String) row.get("payload"));
        assertThat(payload.get("pingId").asString()).isEqualTo(pingId);
        assertThat(payload.propertyNames()).isEqualTo(example().propertyNames());
        assertThat((String) row.get("traceparent")).startsWith("00-" + traceId + "-");
    }

    @Test
    @DisplayName("리스너는 같은 PingSent가 두 번 와도 한 번만 처리하고 처리 기록은 한 행이다")
    void listenerHandlesDuplicateOnce(CapturedOutput output) throws Exception {
        String pingId = example().get("pingId").asString();
        UUID duplicated = UUID.randomUUID();
        UUID next = UUID.randomUUID();

        send(duplicated, pingId);
        send(duplicated, pingId);
        // 같은 키라 같은 파티션에 순서대로 들어간다. 뒤 이벤트가 처리되면 앞의 중복도 소비된 것이다.
        send(next, pingId);

        await().atMost(TIMEOUT).until(() -> processedCount(next) == 1);
        assertThat(processedCount(duplicated)).isEqualTo(1);
        assertThat(output.getOut().split("샘플 이벤트를 처리했다: PingSent " + duplicated, -1)).hasSize(2);
    }

    /** Debezium Outbox Event Router처럼 공통 필드를 UTF-8 헤더로 넣고 값에는 페이로드만 담는다. */
    private void send(UUID eventId, String pingId) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, pingId, exampleJson());
        record.headers().add("id", utf8(eventId.toString()));
        record.headers().add("type", utf8("PingSent"));
        record.headers().add("occurredAt", utf8("2026-10-18T03:04:05.123456Z"));
        record.headers().add("schemaVersion", utf8("1"));
        producer.send(record).get();
    }

    private int processedCount(UUID eventId) {
        return jdbc.sql(
                        "SELECT count(*) FROM processed_event WHERE event_id = :id AND consumer ="
                                + " :consumer")
                .param("id", eventId)
                .param("consumer", LISTENER)
                .query(Integer.class)
                .single();
    }

    private JsonNode example() {
        return jsonMapper.readTree(exampleJson());
    }

    private static String exampleJson() {
        try (InputStream in =
                PingEventIntegrationTest.class.getResourceAsStream("/events/PingSent.json")) {
            if (in == null) {
                throw new IllegalStateException("events/PingSent.json이 없다");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {

        @Bean
        NewTopic pingTopic() {
            return new NewTopic(TOPIC, 3, (short) 1);
        }

        @Bean
        NewTopic pingDeadLetterTopic() {
            return new NewTopic(TOPIC + ".DLT", 3, (short) 1);
        }
    }
}
