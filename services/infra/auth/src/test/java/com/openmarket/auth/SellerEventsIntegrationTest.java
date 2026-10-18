package com.openmarket.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.command.domain.EmailAddress;
import com.openmarket.auth.command.domain.Role;
import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.test.container.RedisContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
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
 * member의 판매자 이벤트(Debezium과 같은 형식) → 리스너 → {@code SELLER} 역할 변경. 페이로드는 발행자(member)의 JSON 예시를 쓰고
 * sellerId만 테스트 계정으로 바꾼다.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=true")
@Import({
    PostgresContainerConfiguration.class,
    RedisContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    SellerEventsIntegrationTest.Topics.class
})
class SellerEventsIntegrationTest {

    static final String TOPIC = "member.seller";
    static final Path PUBLISHER_EXAMPLES = Path.of("../../core/member/src/test/resources/events");

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

        // 같은 컨텍스트의 탈퇴 리스너가 구독하는 토픽. 없으면 그 리스너가 오류를 남긴다.
        @Bean
        NewTopic memberMember() {
            return TopicBuilder.name("member.member").partitions(1).build();
        }
    }

    @Autowired AccountRepository accounts;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired KafkaContainer kafka;

    KafkaProducer<String, String> producer;
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
        accountId = UUID.randomUUID();
        accounts.save(
                Account.register(
                        accountId,
                        EmailAddress.of("seller-" + accountId + "@example.com"),
                        "{bcrypt}x",
                        Instant.now()));
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    @DisplayName("승인하면 SELLER가 붙고, 같은 이벤트가 다시 와도 한 번만 처리한다. 정지하면 떼고 해제하면 다시 붙인다")
    void changesSellerRole() throws Exception {
        UUID approved = UUID.randomUUID();
        UUID rejected = UUID.randomUUID();
        send(approved, "SellerApproved");
        send(approved, "SellerApproved");
        send(rejected, "SellerRejected");
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(approved) == 1);
        assertThat(roles()).contains(Role.SELLER);
        assertThat(processed(rejected)).isZero();

        UUID suspended = UUID.randomUUID();
        send(suspended, "SellerSuspended");
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(suspended) == 1);
        assertThat(roles()).doesNotContain(Role.SELLER);

        UUID reinstated = UUID.randomUUID();
        send(reinstated, "SellerReinstated");
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(reinstated) == 1);
        assertThat(roles()).contains(Role.SELLER);
    }

    private void send(UUID eventId, String type) throws Exception {
        ObjectNode payload =
                (ObjectNode)
                        json.readTree(Files.readString(PUBLISHER_EXAMPLES.resolve(type + ".json")));
        String idField = payload.has("sellerId") ? "sellerId" : "memberId";
        payload.put(idField, accountId.toString());
        ProducerRecord<String, String> record =
                new ProducerRecord<>(TOPIC, accountId.toString(), payload.toString());
        record.headers().add("id", utf8(eventId.toString()));
        record.headers().add("type", utf8(type));
        record.headers().add("occurredAt", utf8("2026-10-18T03:04:05.123456Z"));
        record.headers().add("schemaVersion", utf8("1"));
        producer.send(record).get();
    }

    private Set<Role> roles() {
        return accounts.findById(accountId).orElseThrow().roles();
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
