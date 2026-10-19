package com.openmarket.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.catalog.command.application.PublishProductCommand;
import com.openmarket.catalog.command.application.PublishProductUseCase;
import com.openmarket.catalog.command.application.RegisterProductCommand;
import com.openmarket.catalog.command.application.RegisterProductUseCase;
import com.openmarket.catalog.command.application.RegisterSellerCommand;
import com.openmarket.catalog.command.application.RegisterSellerUseCase;
import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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

    @Autowired RegisterSellerUseCase registerSeller;

    @Autowired RegisterProductUseCase registerProduct;

    @Autowired PublishProductUseCase publishProduct;

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

    @Test
    @DisplayName("SellerSuspended는 판매 중인 상품을 멈추고, SellerReinstated는 다시 연다")
    void suspendAndReinstate() throws Exception {
        UUID sellerId = UUID.randomUUID();
        registerSeller.execute(new RegisterSellerCommand(sellerId, "길동상회"));
        UUID productId =
                registerProduct.execute(
                        new RegisterProductCommand(
                                sellerId,
                                UUID.fromString("0199f000-0000-7000-8000-000000000101"),
                                "반팔 티셔츠",
                                "면 100%",
                                List.of(UUID.randomUUID()),
                                List.of(),
                                List.of(new RegisterProductCommand.Sku(List.of(), 19_900, 1))));
        publishProduct.execute(new PublishProductCommand(sellerId, productId));

        UUID suspendedEvent = UUID.randomUUID();
        send(
                suspendedEvent,
                "SellerSuspended",
                withSellerId("SellerSuspended.json", sellerId),
                sellerId.toString());
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(suspendedEvent) == 1);
        assertThat(productStatus(productId)).isEqualTo("STOPPED/SELLER_SUSPENDED");

        UUID reinstatedEvent = UUID.randomUUID();
        send(
                reinstatedEvent,
                "SellerReinstated",
                withSellerId("SellerReinstated.json", sellerId),
                sellerId.toString());
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(reinstatedEvent) == 1);
        assertThat(productStatus(productId)).isEqualTo("ON_SALE/");
    }

    /** 발행자 예시를 읽고 sellerId만 바꾼다. 필드 구성은 예시 그대로다. */
    private String withSellerId(String example, UUID sellerId) throws Exception {
        ObjectNode node =
                (ObjectNode) json.readTree(Files.readString(PUBLISHER_EXAMPLES.resolve(example)));
        node.put("sellerId", sellerId.toString());
        return json.writeValueAsString(node);
    }

    private String productStatus(UUID productId) {
        return jdbc.sql(
                        "SELECT status || '/' || coalesce(stop_reason, '') FROM product WHERE id = :id")
                .param("id", productId)
                .query(String.class)
                .single();
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
