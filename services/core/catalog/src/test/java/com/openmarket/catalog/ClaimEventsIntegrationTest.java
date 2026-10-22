package com.openmarket.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * fulfillment가 낸 {@code ClaimCompleted}(Debezium과 같은 형식) → 리스너 → 재입고(docs/architecture/catalog.md
 * "클레임 재입고"). 페이로드는 발행자(fulfillment)의 JSON 예시를 읽어 ID만 바꾼다.
 */
@SpringBootTest
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    ClaimEventsIntegrationTest.Topics.class
})
class ClaimEventsIntegrationTest {

    static final String TOPIC = "fulfillment.claim";
    static final Path PUBLISHER_EXAMPLES = Path.of("../fulfillment/src/test/resources/events");

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

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired KafkaContainer kafka;

    @Autowired RegisterSellerUseCase registerSeller;

    @Autowired RegisterProductUseCase registerProduct;

    KafkaProducer<String, String> producer;
    UUID productId;
    UUID skuId;

    @BeforeEach
    void setUp() {
        producer =
                new KafkaProducer<>(
                        Map.of(
                                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                kafka.getBootstrapServers()),
                        new StringSerializer(),
                        new StringSerializer());
        UUID sellerId = UUID.randomUUID();
        registerSeller.execute(new RegisterSellerCommand(sellerId, "길동상회"));
        productId =
                registerProduct.execute(
                        new RegisterProductCommand(
                                sellerId,
                                UUID.fromString("0199f000-0000-7000-8000-000000000101"),
                                "반팔 티셔츠",
                                "면 100%",
                                List.of(UUID.randomUUID()),
                                List.of("사이즈"),
                                List.of(new RegisterProductCommand.Sku(List.of("M"), 19_900, 0))));
        skuId =
                jdbc.sql("SELECT id FROM sku WHERE product_id = :id")
                        .param("id", productId)
                        .query(UUID.class)
                        .single();
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    @DisplayName(
            "ClaimCompleted를 받으면 재입고하고 이력을 남긴다. 같은 클레임이 다른 이벤트 ID로 다시 와도 한 번이다. 다른 클레임 이벤트는 넘긴다")
    void restocks() throws Exception {
        UUID claimId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        send(UUID.randomUUID(), "ClaimRequested", claimId, 2);
        send(first, "ClaimCompleted", claimId, 2);
        send(first, "ClaimCompleted", claimId, 2);
        send(second, "ClaimCompleted", claimId, 2);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.sql(
                                                                "SELECT count(*) FROM processed_event"
                                                                        + " WHERE consumer ="
                                                                        + " 'catalog.claim-completed'"
                                                                        + " AND event_id IN (:ids)")
                                                        .param("ids", List.of(first, second))
                                                        .query(Integer.class)
                                                        .single())
                                        .isEqualTo(2));
        assertThat(
                        jdbc.sql("SELECT on_hand FROM stock WHERE sku_id = :id")
                                .param("id", skuId)
                                .query(Integer.class)
                                .single())
                .isEqualTo(2);
        assertThat(
                        jdbc.sql(
                                        "SELECT delta || ' ' || reason FROM stock_adjustment"
                                                + " WHERE claim_id = :id")
                                .param("id", claimId)
                                .query(String.class)
                                .list())
                .containsExactly("2 클레임 재입고");
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM outbox_event WHERE type ="
                                                + " 'StockAvailabilityChanged' AND aggregate_id = :id")
                                .param("id", productId.toString())
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);
    }

    private void send(UUID eventId, String type, UUID claimId, int quantity) throws Exception {
        ObjectNode payload =
                (ObjectNode)
                        json.readTree(Files.readString(PUBLISHER_EXAMPLES.resolve(type + ".json")));
        payload.put("claimId", claimId.toString());
        if (payload.has("restockLines")) {
            ArrayNode lines = (ArrayNode) payload.get("restockLines");
            ((ObjectNode) lines.get(0)).put("skuId", skuId.toString()).put("quantity", quantity);
        }
        String key = payload.get("sellerOrderId").asString();
        ProducerRecord<String, String> record =
                new ProducerRecord<>(TOPIC, key, json.writeValueAsString(payload));
        record.headers().add("id", eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("type", type.getBytes(StandardCharsets.UTF_8));
        record.headers().add("occurredAt", "2026-10-22T00:00:00Z".getBytes(StandardCharsets.UTF_8));
        record.headers().add("schemaVersion", "1".getBytes(StandardCharsets.UTF_8));
        producer.send(record).get();
    }
}
