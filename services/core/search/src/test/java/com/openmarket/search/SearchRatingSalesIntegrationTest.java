package com.openmarket.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.search.command.application.IndexProductSnapshotCommand;
import com.openmarket.search.command.application.IndexProductSnapshotUseCase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * review의 {@code RatingSummaryChanged}, fulfillment의 {@code PurchaseConfirmed}(Debezium과 같은 형식) →
 * 리스너 → 검색 문서의 별점·판매량 → 정렬·필터(docs/architecture/search.md "별점", "판매량", "정렬과 필터"). 페이로드는 발행자의 JSON
 * 예시를 읽어 ID와 값만 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    ElasticsearchContainerConfiguration.class,
    SearchIntegrationTest.Topics.class
})
class SearchRatingSalesIntegrationTest {

    static final Path REVIEW_EXAMPLES = Path.of("../review/src/test/resources/events");
    static final Path FULFILLMENT_EXAMPLES = Path.of("../fulfillment/src/test/resources/events");

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper json;
    @Autowired KafkaContainer kafka;
    @Autowired ElasticsearchClient elasticsearch;
    @Autowired IndexProductSnapshotUseCase indexSnapshot;
    @Autowired JdbcClient jdbc;

    KafkaProducer<String, String> producer;
    UUID leafCategory;

    @BeforeEach
    void setUp() {
        producer =
                new KafkaProducer<>(
                        Map.of(
                                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                kafka.getBootstrapServers()),
                        new StringSerializer(),
                        new StringSerializer());
        leafCategory = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    @DisplayName("별점 요약을 받으면 응답에 평균·리뷰 수가 보이고, RATING 정렬과 minRating 필터가 된다. 오래된 리비전은 무시한다")
    void sortsAndFiltersByRating() throws Exception {
        Instant base = Instant.parse("2026-10-20T00:00:00Z");
        UUID good = index("반팔 티셔츠 A", base);
        UUID best = index("반팔 티셔츠 B", base.plusSeconds(10));
        UUID popular = index("반팔 티셔츠 C", base.plusSeconds(20));
        UUID none = index("반팔 티셔츠 D", base.plusSeconds(30));

        rating(good, 1, "4.0", 1);
        rating(best, 3, "4.7", 3);
        rating(popular, 10, "4.0", 10);
        // 늦게 온 오래된 리비전은 무시한다
        rating(best, 1, "1.0", 1);
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () ->
                                assertThat(ids(category().param("sort", "RATING")))
                                        .containsExactly(best, popular, good, none));

        JsonNode first = search(category().param("sort", "RATING")).get("items").get(0);
        assertThat(first.get("ratingAverage").decimalValue()).isEqualByComparingTo("4.7");
        assertThat(first.get("ratingCount").asLong()).isEqualTo(3);
        JsonNode last = search(category().param("sort", "RATING")).get("items").get(3);
        assertThat(last.get("ratingAverage").isNull()).isTrue();
        assertThat(last.get("ratingCount").asLong()).isZero();

        assertThat(ids(category().param("minRating", "4").param("sort", "RATING")))
                .containsExactly(best, popular, good);
        assertThat(ids(category().param("minRating", "5"))).isEmpty();
        mockMvc.perform(category().param("minRating", "6")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("구매확정 수량을 더해 SALES 정렬이 되고, 같은 하위 주문은 다른 이벤트 ID로 다시 와도 한 번만 센다")
    void sortsBySales() throws Exception {
        Instant base = Instant.parse("2026-10-20T00:00:00Z");
        UUID few = index("무선 이어폰 A", base);
        UUID many = index("무선 이어폰 B", base.plusSeconds(10));
        UUID zero = index("무선 이어폰 C", base.plusSeconds(20));

        UUID sellerOrder = UUID.randomUUID();
        String twoOfMany = confirmed(sellerOrder, Map.of(many, 2));
        send("fulfillment.shipment", "PurchaseConfirmed", UUID.randomUUID(), twoOfMany);
        send("fulfillment.shipment", "PurchaseConfirmed", UUID.randomUUID(), twoOfMany);
        send(
                "fulfillment.shipment",
                "PurchaseConfirmed",
                UUID.randomUUID(),
                confirmed(UUID.randomUUID(), Map.of(many, 3, few, 1)));
        // 다른 이벤트는 넘긴다
        send(
                "fulfillment.shipment",
                "ShipmentDelivered",
                UUID.randomUUID(),
                confirmed(UUID.randomUUID(), Map.of(zero, 100)));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () ->
                                assertThat(salesOf(many))
                                        .isEqualTo("5/2")); // 2 + 3, 리비전 2(중복은 세지 않음)
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () ->
                                assertThat(ids(category().param("sort", "SALES")))
                                        .containsExactly(many, few, zero));
        assertThat(salesOf(few)).isEqualTo("1/1");
        assertThat(salesOf(zero)).isNull();
    }

    @Test
    @DisplayName("관련도가 같으면 판매량 많은 순이다")
    void breaksRelevanceTiesBySales() throws Exception {
        Instant base = Instant.parse("2026-10-20T00:00:00Z");
        UUID newer = index("면 양말 세트", base.plusSeconds(10));
        UUID older = index("면 양말 세트", base);
        send(
                "fulfillment.shipment",
                "PurchaseConfirmed",
                UUID.randomUUID(),
                confirmed(UUID.randomUUID(), Map.of(older, 4)));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () ->
                                assertThat(ids(category().param("q", "양말")))
                                        .containsExactly(older, newer));
    }

    private UUID index(String name, Instant publishedAt) throws Exception {
        UUID productId = UUID.randomUUID();
        indexSnapshot.execute(
                new IndexProductSnapshotCommand(
                        productId,
                        2,
                        "ON_SALE",
                        UUID.randomUUID(),
                        "길동상회",
                        leafCategory,
                        List.of(UUID.randomUUID(), leafCategory),
                        name,
                        name + " 상품입니다.",
                        List.of(UUID.randomUUID()),
                        19_900,
                        true,
                        1,
                        publishedAt));
        elasticsearch.indices().refresh(r -> r.index("products"));
        return productId;
    }

    private void rating(UUID productId, long count, String average, long revision)
            throws Exception {
        ObjectNode node =
                (ObjectNode)
                        json.readTree(
                                Files.readString(
                                        REVIEW_EXAMPLES.resolve("RatingSummaryChanged.json")));
        node.put("productId", productId.toString());
        node.put("ratingCount", count);
        node.put("ratingAverage", new java.math.BigDecimal(average));
        node.put("revision", revision);
        send(
                "review.rating-summary",
                "RatingSummaryChanged",
                UUID.randomUUID(),
                json.writeValueAsString(node));
    }

    /** 발행자 예시를 읽어 하위 주문과 항목(상품, 수량)을 바꾼다. */
    private String confirmed(UUID sellerOrderId, Map<UUID, Integer> quantities) throws Exception {
        ObjectNode node =
                (ObjectNode)
                        json.readTree(
                                Files.readString(
                                        FULFILLMENT_EXAMPLES.resolve("PurchaseConfirmed.json")));
        node.put("sellerOrderId", sellerOrderId.toString());
        ArrayNode lines = (ArrayNode) node.get("lines");
        ObjectNode template = (ObjectNode) lines.get(0);
        lines.removeAll();
        quantities.forEach(
                (productId, quantity) -> {
                    ObjectNode line = template.deepCopy();
                    line.put("orderLineId", UUID.randomUUID().toString());
                    line.put("productId", productId.toString());
                    line.put("quantity", quantity);
                    lines.add(line);
                });
        return json.writeValueAsString(node);
    }

    private String salesOf(UUID productId) {
        return jdbc.sql(
                        "SELECT sales_count || '/' || revision FROM product_sales WHERE product_id"
                                + " = :id")
                .param("id", productId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private MockHttpServletRequestBuilder category() {
        return get("/v1/products").param("categoryId", leafCategory.toString());
    }

    private JsonNode search(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(
                mockMvc.perform(request)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8));
    }

    private List<UUID> ids(MockHttpServletRequestBuilder request) throws Exception {
        List<UUID> ids = new ArrayList<>();
        search(request)
                .get("items")
                .forEach(item -> ids.add(UUID.fromString(item.get("productId").asString())));
        return ids;
    }

    private void send(String topic, String type, UUID eventId, String payload) throws Exception {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, UUID.randomUUID().toString(), payload);
        record.headers().add("id", eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("type", type.getBytes(StandardCharsets.UTF_8));
        record.headers()
                .add("occurredAt", "2026-10-28T07:00:00.123456Z".getBytes(StandardCharsets.UTF_8));
        record.headers().add("schemaVersion", "1".getBytes(StandardCharsets.UTF_8));
        producer.send(record).get();
    }
}
