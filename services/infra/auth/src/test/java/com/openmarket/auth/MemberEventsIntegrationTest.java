package com.openmarket.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.auth.command.application.RegisterAccountCommand;
import com.openmarket.auth.command.application.RegisterAccountUseCase;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.test.container.RedisContainerConfiguration;
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
import tools.jackson.databind.node.ObjectNode;

/**
 * member의 {@code MemberWithdrawn}(Debezium과 같은 형식) → 리스너 → 계정 비활성화, 인가 기록 삭제. 페이로드는 발행자(member)의
 * JSON 예시를 쓰고 memberId만 테스트 계정으로 바꾼다.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=true")
@Import({
    PostgresContainerConfiguration.class,
    RedisContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    MemberEventsIntegrationTest.Topics.class
})
class MemberEventsIntegrationTest {

    static final String TOPIC = "member.member";
    static final Path PUBLISHER_EXAMPLES = Path.of("../../core/member/src/test/resources/events");

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic memberMember() {
            return TopicBuilder.name(TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic memberMemberDlt() {
            return TopicBuilder.name(TOPIC + ".DLT").partitions(1).build();
        }

        // 같은 컨텍스트의 판매자 리스너가 구독하는 토픽. 없으면 그 리스너가 오류를 남긴다.
        @Bean
        NewTopic memberSeller() {
            return TopicBuilder.name("member.seller").partitions(1).build();
        }
    }

    @Autowired RegisterAccountUseCase registerAccount;

    @Autowired AccountRepository accounts;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired KafkaContainer kafka;

    KafkaProducer<String, String> producer;
    String email;
    UUID accountId;

    @BeforeEach
    void setUp() {
        producer =
                new KafkaProducer<>(
                        Map.of(
                                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                kafka.getBootstrapServers()),
                        new StringSerializer(),
                        new StringSerializer());
        email = "leaving-" + UUID.randomUUID() + "@example.com";
        accountId =
                registerAccount.execute(
                        new RegisterAccountCommand(email, "leaving-pass-1234", "홍길동"));
        jdbc.sql(
                        "INSERT INTO oauth2_authorization (id, registered_client_id, principal_name,"
                                + " authorization_grant_type) VALUES (:id, 'shop', :principal,"
                                + " 'authorization_code')")
                .param("id", UUID.randomUUID().toString())
                .param("principal", accountId.toString())
                .update();
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    @DisplayName("탈퇴 이벤트가 오면 계정을 비활성화하고 인가 기록을 지운다. 두 번 와도 한 번만 처리하고, 같은 이메일로 다시 가입할 수 있다")
    void deactivatesAccount() throws Exception {
        UUID eventId = UUID.randomUUID();

        send(eventId);
        send(eventId);

        await().atMost(Duration.ofSeconds(30)).until(() -> processed(eventId) == 1);
        assertThat(accounts.findById(accountId).orElseThrow().isActive()).isFalse();
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM oauth2_authorization WHERE"
                                                + " principal_name = :principal")
                                .param("principal", accountId.toString())
                                .query(Integer.class)
                                .single())
                .isZero();

        UUID again =
                registerAccount.execute(
                        new RegisterAccountCommand(email, "again-pass-1234", "홍길동"));
        assertThat(again).isNotEqualTo(accountId);
    }

    private void send(UUID eventId) throws Exception {
        ObjectNode payload =
                (ObjectNode)
                        json.readTree(
                                Files.readString(
                                        PUBLISHER_EXAMPLES.resolve("MemberWithdrawn.json")));
        payload.put("memberId", accountId.toString());
        ProducerRecord<String, String> record =
                new ProducerRecord<>(TOPIC, accountId.toString(), payload.toString());
        record.headers().add("id", utf8(eventId.toString()));
        record.headers().add("type", utf8("MemberWithdrawn"));
        record.headers().add("occurredAt", utf8("2026-10-18T03:04:05.123456Z"));
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
