package com.openmarket.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.openmarket.catalog.command.application.RegisterProductCommand;
import com.openmarket.catalog.command.application.RegisterProductUseCase;
import com.openmarket.catalog.command.application.RegisterSellerCommand;
import com.openmarket.catalog.command.application.RegisterSellerUseCase;
import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.io.IOException;
import java.io.InputStream;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * order가 낸 주문 이벤트(Debezium과 같은 형식) → 리스너 → 재고 예약·확정·해제(docs/architecture/catalog.md "재고 예약"). 페이로드는
 * 발행자(order)의 JSON 예시를 읽어 ID만 바꾼다.
 */
@SpringBootTest
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    OrderEventsIntegrationTest.Topics.class
})
class OrderEventsIntegrationTest {

    static final String TOPIC = "order.order";
    static final Path PUBLISHER_EXAMPLES = Path.of("../order/src/test/resources/events");

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic orderOrder() {
            return TopicBuilder.name(TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic orderOrderDlt() {
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
    UUID skuM;
    UUID skuL;

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
                                List.of(
                                        new RegisterProductCommand.Sku(List.of("M"), 19_900, 2),
                                        new RegisterProductCommand.Sku(List.of("L"), 19_900, 1))));
        List<UUID> skus =
                jdbc.sql("SELECT id FROM sku WHERE product_id = :id ORDER BY position")
                        .param("id", productId)
                        .query(UUID.class)
                        .list();
        skuM = skus.get(0);
        skuL = skus.get(1);
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    @DisplayName("OrderPlaced를 받으면 예약하고 StockReserved를 낸다. 같은 이벤트가 다시 와도 한 번만 예약한다")
    void reservesOnOrderPlaced() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        send(eventId, "OrderPlaced", placed(orderId, skuM, 2), orderId);
        send(eventId, "OrderPlaced", placed(orderId, skuM, 2), orderId);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(status(orderId)).isEqualTo("RESERVED"));
        assertThat(reserved(skuM)).isEqualTo(2);
        assertThat(outboxTypes(orderId)).containsExactly("StockReserved");
        assertThat(json.readTree(outboxPayload(orderId, "StockReserved")).propertyNames())
                .isEqualTo(example("StockReserved").propertyNames());
        // SKU M이 모두 예약돼도 L이 남아 상품은 판매 가능하다.
        assertThat(outboxTypes(productId)).isEmpty();
    }

    @Test
    @DisplayName("재고가 모자라면 StockReservationFailed를 내고, 이어서 온 OrderCancelled는 아무것도 바꾸지 않는다")
    void failsAndCancels() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID cancelledEventId = UUID.randomUUID();

        send(UUID.randomUUID(), "OrderPlaced", placed(orderId, skuL, 2), orderId);
        send(cancelledEventId, "OrderCancelled", cancelled(orderId), orderId);

        // 다른 테스트가 남긴 처리 기록과 섞이지 않게 이 이벤트의 처리 기록을 기다린다.
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertThat(processed(cancelledEventId, "catalog.order-cancelled"))
                                        .isTrue());
        assertThat(status(orderId)).isEqualTo("FAILED");
        assertThat(reserved(skuL)).isZero();
        assertThat(outboxTypes(orderId)).containsExactly("StockReservationFailed");
        JsonNode failed = json.readTree(outboxPayload(orderId, "StockReservationFailed"));
        assertThat(failed.propertyNames())
                .isEqualTo(example("StockReservationFailed").propertyNames());
        assertThat(failed.get("insufficientSkuIds").get(0).asString()).isEqualTo(skuL.toString());
    }

    @Test
    @DisplayName("OrderCancelled는 예약을 풀고, OrderPaid는 예약을 확정해 실재고에서 뺀다")
    void releasesAndCommits() throws Exception {
        UUID cancelledOrder = UUID.randomUUID();
        UUID paidOrder = UUID.randomUUID();

        send(UUID.randomUUID(), "OrderPlaced", placed(cancelledOrder, skuL, 1), cancelledOrder);
        send(UUID.randomUUID(), "OrderPlaced", placed(paidOrder, skuM, 1), paidOrder);
        send(UUID.randomUUID(), "OrderCancelled", cancelled(cancelledOrder), cancelledOrder);
        send(UUID.randomUUID(), "OrderPaid", paid(paidOrder), paidOrder);
        send(UUID.randomUUID(), "OrderAwaitingPayment", "{}", paidOrder);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () -> {
                            assertThat(status(cancelledOrder)).isEqualTo("RELEASED");
                            assertThat(status(paidOrder)).isEqualTo("COMMITTED");
                        });
        assertThat(reserved(skuL)).isZero();
        assertThat(onHand(skuL)).isEqualTo(1);
        assertThat(reserved(skuM)).isZero();
        assertThat(onHand(skuM)).isEqualTo(1);
        // L의 예약과 해제로 상품 판매 가능 여부가 바뀌지 않았다(M이 남아 있었다).
        assertThat(outboxTypes(productId)).isEmpty();
    }

    @Test
    @DisplayName("상품의 마지막 재고를 예약하면 품절 전환 이벤트를 내고, 풀면 재입고 이벤트를 낸다")
    void availabilityChanges() throws Exception {
        UUID orderId = UUID.randomUUID();
        String payload = placed(orderId, skuM, 2);
        ObjectNode node = (ObjectNode) json.readTree(payload);
        ((ArrayNode) node.get("lines"))
                .addObject()
                .put("skuId", skuL.toString())
                .put("quantity", 1);

        send(UUID.randomUUID(), "OrderPlaced", json.writeValueAsString(node), orderId);
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(outboxTypes(productId)).hasSize(1));
        send(UUID.randomUUID(), "OrderCancelled", cancelled(orderId), orderId);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(outboxTypes(productId)).hasSize(2));
        List<String> inStock =
                jdbc.sql(
                                "SELECT payload->>'inStock' FROM outbox_event WHERE aggregate_type = 'catalog.stock'"
                                        + " AND aggregate_id = :id"
                                        + " ORDER BY occurred_at, id")
                        .param("id", productId.toString())
                        .query(String.class)
                        .list();
        assertThat(inStock).containsExactly("false", "true");
    }

    /** 발행자 예시를 읽어 orderId와 항목을 바꾼다. 필드 구성은 예시 그대로다. */
    private String placed(UUID orderId, UUID skuId, int quantity) throws IOException {
        ObjectNode node =
                (ObjectNode)
                        json.readTree(
                                Files.readString(PUBLISHER_EXAMPLES.resolve("OrderPlaced.json")));
        node.put("orderId", orderId.toString());
        ArrayNode lines = node.putArray("lines");
        lines.addObject().put("skuId", skuId.toString()).put("quantity", quantity);
        return json.writeValueAsString(node);
    }

    private String cancelled(UUID orderId) throws IOException {
        ObjectNode node =
                (ObjectNode)
                        json.readTree(
                                Files.readString(
                                        PUBLISHER_EXAMPLES.resolve("OrderCancelled.json")));
        node.put("orderId", orderId.toString());
        return json.writeValueAsString(node);
    }

    /** catalog는 orderId만 읽는다. 나머지 필드는 예시 그대로 두어 역직렬화가 깨지지 않는지 본다. */
    private String paid(UUID orderId) throws IOException {
        ObjectNode node =
                (ObjectNode)
                        json.readTree(
                                Files.readString(PUBLISHER_EXAMPLES.resolve("OrderPaid.json")));
        node.put("orderId", orderId.toString());
        return json.writeValueAsString(node);
    }

    private JsonNode example(String type) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/events/" + type + ".json")) {
            return json.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private String status(UUID orderId) {
        return jdbc.sql("SELECT status FROM stock_reservation WHERE order_id = :id")
                .param("id", orderId)
                .query(String.class)
                .optional()
                .orElse("");
    }

    private int reserved(UUID skuId) {
        return jdbc.sql("SELECT reserved FROM stock WHERE sku_id = :id")
                .param("id", skuId)
                .query(Integer.class)
                .single();
    }

    private int onHand(UUID skuId) {
        return jdbc.sql("SELECT on_hand FROM stock WHERE sku_id = :id")
                .param("id", skuId)
                .query(Integer.class)
                .single();
    }

    private List<String> outboxTypes(UUID aggregateId) {
        return jdbc.sql(
                        "SELECT type FROM outbox_event WHERE aggregate_type = 'catalog.stock'"
                                + " AND aggregate_id = :id ORDER BY occurred_at, id")
                .param("id", aggregateId.toString())
                .query(String.class)
                .list();
    }

    private String outboxPayload(UUID orderId, String type) {
        return jdbc.sql(
                        "SELECT payload::text FROM outbox_event WHERE aggregate_id = :id AND type = :type")
                .param("id", orderId.toString())
                .param("type", type)
                .query(String.class)
                .single();
    }

    private boolean processed(UUID eventId, String consumer) {
        return jdbc.sql(
                                "SELECT count(*) FROM processed_event WHERE event_id = :eventId"
                                        + " AND consumer = :consumer")
                        .param("eventId", eventId)
                        .param("consumer", consumer)
                        .query(Integer.class)
                        .single()
                == 1;
    }

    private void send(UUID eventId, String type, String payload, UUID key) throws Exception {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(TOPIC, key.toString(), payload);
        record.headers().add("id", utf8(eventId.toString()));
        record.headers().add("type", utf8(type));
        record.headers().add("occurredAt", utf8("2026-10-20T03:04:05.123456Z"));
        record.headers().add("schemaVersion", utf8("1"));
        producer.send(record).get();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
