package com.openmarket.common.messaging.deadletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;

/**
 * DLT 메시지 관리자 API(docs/architecture/messaging.md "DLT 재처리"). 목록, 다시 보내기, 버리기와 상태 검사, 감사 로그를 본다.
 * 보안(ADMIN 역할)은 서비스가 맡는다. 여기서는 요청의 principal로 관리자를 흉내 낸다.
 */
@SpringBootTest(
        properties = {
            "spring.application.name=catalog",
            "spring.kafka.listener.auto-startup=false",
            // 감사 로그(common-web audit_log)도 함께 확인한다.
            "spring.flyway.locations=classpath:db/common/messaging,classpath:db/common/web"
        })
@AutoConfigureMockMvc
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    DeadLetterAdminIntegrationTest.Topics.class
})
class DeadLetterAdminIntegrationTest {

    static final String TOPIC = "dlt-admin.order";
    static final String DLT = TOPIC + ".DLT";
    static final Principal ADMIN = () -> "admin-1";

    /** DLT 위치가 겹치지 않게 매긴다. */
    static final AtomicLong DLT_OFFSET = new AtomicLong();

    @Autowired MockMvc mockMvc;
    @Autowired DeadLetterRecorder recorder;
    @Autowired JdbcClient jdbc;
    @Autowired KafkaContainer kafka;

    @Test
    @DisplayName("목록은 쌓인 시각 내림차순이고 상태·토픽으로 거르며 커서로 넘긴다. 페이로드와 예외 메시지는 주지 않는다")
    void listsWithoutPayload() throws Exception {
        String topic = "dlt-admin.list-" + UUID.randomUUID();
        UUID older = deadLetter(topic, 0, 10, Instant.parse("2026-10-27T01:00:00Z"));
        UUID newer = deadLetter(topic, 0, 11, Instant.parse("2026-10-27T02:00:00Z"));

        String first =
                mockMvc.perform(
                                get("/v1/admin/dead-letters")
                                        .principal(ADMIN)
                                        .param("topic", topic)
                                        .param("status", "PENDING")
                                        .param("size", "1"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items.length()").value(1))
                        .andExpect(jsonPath("$.items[0].eventId").value(newer.toString()))
                        .andExpect(jsonPath("$.items[0].eventType").value("OrderPlaced"))
                        .andExpect(jsonPath("$.items[0].consumerGroup").value("catalog"))
                        .andExpect(jsonPath("$.items[0].originalTopic").value(topic))
                        .andExpect(jsonPath("$.items[0].originalOffset").value(11))
                        .andExpect(
                                jsonPath("$.items[0].exceptionClass")
                                        .value(IllegalStateException.class.getName()))
                        .andExpect(
                                jsonPath("$.items[0].traceId")
                                        .value("4bf92f3577b34da6a3ce929d0e0e4736"))
                        .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                        .andExpect(jsonPath("$.items[0].payload").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(first).doesNotContain("secret-payload").doesNotContain("boom-message");

        mockMvc.perform(
                        get("/v1/admin/dead-letters")
                                .principal(ADMIN)
                                .param("topic", topic)
                                .param("size", "1")
                                .param("before", JsonPath.<String>read(first, "$.nextCursor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].eventId").value(older.toString()))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        mockMvc.perform(get("/v1/admin/dead-letters").principal(ADMIN).param("status", "LOST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEAD_LETTER_INVALID_INPUT"));
    }

    @Test
    @DisplayName("다시 보내면 원래 토픽·파티션에 원래 키·값·헤더와 대상 그룹 헤더로 보내고 RETRIED가 되며 감사 로그를 남긴다. 다시 누르면 409")
    void retriesToOriginalTopicForTargetGroup() throws Exception {
        UUID eventId = deadLetter(TOPIC, 1, 7, Instant.now());
        UUID id = rowId(eventId);

        mockMvc.perform(post("/v1/admin/dead-letters/{id}/retry", id).principal(ADMIN))
                .andExpect(status().isNoContent());

        ConsumerRecord<String, String> sent = consumeFromOriginal(eventId);
        assertThat(sent.partition()).isEqualTo(1);
        assertThat(sent.key()).isEqualTo("order-" + eventId);
        assertThat(sent.value()).isEqualTo("secret-payload");
        assertThat(header(sent, "type")).isEqualTo("OrderPlaced");
        assertThat(header(sent, "dlt-target-group")).isEqualTo("catalog");
        assertThat(sent.headers().toArray()).noneMatch(h -> h.key().startsWith("kafka_dlt-"));
        assertThat(row(id))
                .containsEntry("status", "RETRIED")
                .containsEntry("resolved_by", "admin-1");
        assertThat(audit(id)).containsExactly("DEAD_LETTER_RETRIED");

        mockMvc.perform(post("/v1/admin/dead-letters/{id}/retry", id).principal(ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAD_LETTER_NOT_PENDING"));
    }

    @Test
    @DisplayName("버리기는 사유가 있어야 하고 DISCARDED가 되며 감사 로그를 남긴다. 버린 메시지는 다시 보낼 수 없다")
    void discardsWithReason() throws Exception {
        UUID id = rowId(deadLetter(TOPIC, 0, 8, Instant.now()));

        mockMvc.perform(
                        post("/v1/admin/dead-letters/{id}/discard", id)
                                .principal(ADMIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEAD_LETTER_INVALID_INPUT"));

        mockMvc.perform(
                        post("/v1/admin/dead-letters/{id}/discard", id)
                                .principal(ADMIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\": \"이미 수동으로 반영함\"}"))
                .andExpect(status().isNoContent());

        assertThat(row(id))
                .containsEntry("status", "DISCARDED")
                .containsEntry("discard_reason", "이미 수동으로 반영함")
                .containsEntry("resolved_by", "admin-1");
        assertThat(audit(id)).containsExactly("DEAD_LETTER_DISCARDED");
        mockMvc.perform(post("/v1/admin/dead-letters/{id}/retry", id).principal(ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAD_LETTER_NOT_PENDING"));
    }

    @Test
    @DisplayName("없는 메시지는 404 DEAD_LETTER_NOT_FOUND")
    void notFound() throws Exception {
        mockMvc.perform(
                        post("/v1/admin/dead-letters/{id}/retry", UUID.randomUUID())
                                .principal(ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEAD_LETTER_NOT_FOUND"));
    }

    /** Spring Kafka가 DLT에 보낸 것과 같은 모양의 메시지를 수집기 저장 경로로 넣는다. 이벤트 ID를 돌려준다. */
    private UUID deadLetter(String originalTopic, int partition, long offset, Instant at) {
        UUID eventId = UUID.randomUUID();
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        originalTopic + ".DLT",
                        partition,
                        DLT_OFFSET.incrementAndGet(),
                        at.toEpochMilli(),
                        TimestampType.CREATE_TIME,
                        0,
                        0,
                        "order-" + eventId,
                        "secret-payload",
                        new RecordHeaders(),
                        Optional.empty());
        record.headers().add("id", utf8(eventId.toString()));
        record.headers().add("type", utf8("OrderPlaced"));
        record.headers()
                .add(
                        "traceparent",
                        utf8("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"));
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_TOPIC, utf8(originalTopic));
        record.headers()
                .add(
                        KafkaHeaders.DLT_ORIGINAL_PARTITION,
                        ByteBuffer.allocate(4).putInt(partition).array());
        record.headers()
                .add(
                        KafkaHeaders.DLT_ORIGINAL_OFFSET,
                        ByteBuffer.allocate(8).putLong(offset).array());
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, utf8("catalog"));
        record.headers()
                .add(KafkaHeaders.DLT_EXCEPTION_FQCN, utf8(IllegalStateException.class.getName()));
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, utf8("boom-message"));
        assertThat(recorder.record(record)).isTrue();
        return eventId;
    }

    private UUID rowId(UUID eventId) {
        return jdbc.sql("SELECT id FROM dead_letter WHERE event_id = :id")
                .param("id", eventId)
                .query(UUID.class)
                .single();
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.sql("SELECT * FROM dead_letter WHERE id = :id")
                .param("id", id)
                .query()
                .singleRow();
    }

    private List<String> audit(UUID id) {
        return jdbc.sql(
                        "SELECT action FROM audit_log WHERE target_type = 'DEAD_LETTER' AND target_id = :id")
                .param("id", id.toString())
                .query(String.class)
                .list();
    }

    private ConsumerRecord<String, String> consumeFromOriginal(UUID eventId) {
        try (KafkaConsumer<String, String> consumer =
                new KafkaConsumer<>(
                        Map.of(
                                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                kafka.getBootstrapServers(),
                                ConsumerConfig.GROUP_ID_CONFIG,
                                "test-" + UUID.randomUUID(),
                                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                                "earliest"),
                        new StringDeserializer(),
                        new StringDeserializer())) {
            consumer.assign(
                    List.of(
                            new TopicPartition(TOPIC, 0),
                            new TopicPartition(TOPIC, 1),
                            new TopicPartition(TOPIC, 2)));
            consumer.seekToBeginning(consumer.assignment());
            List<ConsumerRecord<String, String>> seen = new ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(seen::add);
                for (ConsumerRecord<String, String> record : seen) {
                    if (eventId.toString().equals(header(record, "id"))) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("원래 토픽에서 다시 보낸 메시지를 찾지 못했다: " + eventId);
    }

    private static @Nullable String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {

        @Bean
        NewTopic deadLetterAdminTopic() {
            return new NewTopic(TOPIC, 3, (short) 1);
        }
    }
}
