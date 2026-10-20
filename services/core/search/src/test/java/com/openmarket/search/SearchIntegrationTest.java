package com.openmarket.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.test.openapi.OpenApiSnapshot;
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
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * catalog 이벤트(Debezium과 같은 형식) → 리스너 → Elasticsearch 색인 → 검색 API. 이벤트 페이로드는 발행자(catalog)의 JSON 예시를
 * 읽고 ID와 이름만 바꾼다(docs/architecture/messaging.md "스키마 규칙"). 테스트마다 무작위 카테고리를 써서 서로의 상품이 섞이지 않게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    ElasticsearchContainerConfiguration.class,
    SearchIntegrationTest.Topics.class
})
class SearchIntegrationTest {

    static final String PRODUCTS = "/v1/products";
    static final Path PUBLISHER_EXAMPLES = Path.of("../catalog/src/test/resources/events");

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic catalogProduct() {
            return TopicBuilder.name("catalog.product").partitions(1).build();
        }

        @Bean
        NewTopic catalogProductDlt() {
            return TopicBuilder.name("catalog.product.DLT").partitions(1).build();
        }

        @Bean
        NewTopic catalogStock() {
            return TopicBuilder.name("catalog.stock").partitions(1).build();
        }

        @Bean
        NewTopic catalogStockDlt() {
            return TopicBuilder.name("catalog.stock.DLT").partitions(1).build();
        }
    }

    @Autowired MockMvc mockMvc;

    @Autowired JsonMapper json;

    @Autowired KafkaContainer kafka;

    @Autowired ElasticsearchClient elasticsearch;

    @Autowired IndexProductSnapshotUseCase indexSnapshot;

    KafkaProducer<String, String> producer;
    UUID topCategory;
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
        topCategory = UUID.randomUUID();
        leafCategory = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    @DisplayName("판매를 시작하면 몇 초 안에 검색에 나오고, 품절되면 inStock=false, 판매를 중지하면 빠진다")
    void indexesCatalogEvents() throws Exception {
        UUID productId = UUID.randomUUID();
        String key = productId.toString();

        send(
                "catalog.product",
                key,
                "ProductRegistered",
                snapshot("ProductRegistered.json", productId, 1, 1));
        send(
                "catalog.product",
                key,
                "ProductPublished",
                snapshot("ProductPublished.json", productId, 2, 1));
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(searchIds(category())).containsExactly(productId));

        send(
                "catalog.stock",
                key,
                "StockAvailabilityChanged",
                withIds(
                        "StockAvailabilityChanged.json",
                        productId,
                        Map.of("inStock", false, "availabilityRevision", 2)));
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () ->
                                assertThat(
                                                search(category())
                                                        .get("items")
                                                        .get(0)
                                                        .get("inStock")
                                                        .asBoolean())
                                        .isFalse());

        send(
                "catalog.product",
                key,
                "ProductStopped",
                withIds("ProductStopped.json", productId, Map.of("revision", 3)));
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(searchIds(category())).isEmpty());
    }

    @Test
    @DisplayName("nori로 붙여 쓴 상품명의 부분어도 찾고, 품절 상품은 관련도가 높아도 뒤로 간다")
    void searchesKoreanKeywordAndPutsOutOfStockLast() throws Exception {
        Fixture f = givenProducts();

        assertThat(searchIds(category().param("q", "티셔츠")))
                .containsExactly(f.menShirt(), f.womenShirt());
        assertThat(searchIds(category().param("q", "이어폰"))).containsExactly(f.earphone());
        assertThat(searchIds(category().param("q", "사과즙"))).isEmpty();
    }

    @Test
    @DisplayName("최신·가격 정렬과 가격대·대분류 필터. 판매 중이 아닌 상품은 나오지 않는다")
    void sortsAndFilters() throws Exception {
        Fixture f = givenProducts();

        assertThat(searchIds(category()))
                .containsExactly(f.earphone(), f.menShirt(), f.womenShirt());
        assertThat(searchIds(category().param("sort", "PRICE_DESC")))
                .containsExactly(f.earphone(), f.menShirt(), f.womenShirt());
        assertThat(searchIds(category().param("sort", "PRICE_ASC")))
                .containsExactly(f.menShirt(), f.earphone(), f.womenShirt());
        assertThat(searchIds(category().param("minPrice", "20000").param("maxPrice", "60000")))
                .containsExactly(f.earphone(), f.womenShirt());
        assertThat(searchIds(get(PRODUCTS).param("categoryId", topCategory.toString()))).hasSize(3);
        assertThat(search(category()).get("totalCount").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("커서로 넘겨 읽으면 한 번에 읽은 것과 순서가 같고, 마지막 쪽의 nextCursor는 null이다. 틀린 커서는 400이다")
    void pagesWithCursor() throws Exception {
        givenProducts();
        List<UUID> all = searchIds(category().param("sort", "PRICE_ASC"));

        List<UUID> paged = new ArrayList<>();
        @Nullable String cursor = null;
        do {
            MockHttpServletRequestBuilder request =
                    category().param("sort", "PRICE_ASC").param("size", "1");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            JsonNode page = search(request);
            page.get("items")
                    .forEach(item -> paged.add(UUID.fromString(item.get("productId").asString())));
            JsonNode next = page.get("nextCursor");
            cursor = next == null || next.isNull() ? null : next.asString();
        } while (cursor != null);

        assertThat(paged).isEqualTo(all);
        mockMvc.perform(category().param("cursor", "!!not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_INVALID_CURSOR"));
        mockMvc.perform(category().param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("OpenAPI 문서가 프론트 api-client에 저장된 문서와 같다")
    void openApiMatchesClientSnapshot() throws Exception {
        String apiDocs =
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        OpenApiSnapshot.assertMatches("search", apiDocs);
    }

    /** 품절인 최신 반팔 티셔츠, 판매 중인 반팔티셔츠·이어폰, 판매 중이 아닌 착즙기. */
    private Fixture givenProducts() throws Exception {
        Instant base = Instant.parse("2026-10-20T00:00:00Z");
        UUID menShirt = index("남성용 반팔티셔츠", 19_900, true, "ON_SALE", base);
        UUID womenShirt = index("여성 반팔 티셔츠", 29_900, false, "ON_SALE", base.plusSeconds(300));
        UUID earphone = index("무선블루투스이어폰 화이트", 59_000, true, "ON_SALE", base.plusSeconds(200));
        index("착즙기 녹즙기", 99_000, true, "STOPPED", base.plusSeconds(400));
        elasticsearch.indices().refresh(r -> r.index("products"));
        return new Fixture(menShirt, womenShirt, earphone);
    }

    private UUID index(
            String name, long price, boolean inStock, String status, Instant publishedAt) {
        UUID productId = UUID.randomUUID();
        indexSnapshot.execute(
                new IndexProductSnapshotCommand(
                        productId,
                        2,
                        status,
                        UUID.randomUUID(),
                        "길동상회",
                        leafCategory,
                        List.of(topCategory, leafCategory),
                        name,
                        name + " 상품입니다.",
                        List.of(UUID.randomUUID()),
                        price,
                        inStock,
                        1,
                        publishedAt));
        return productId;
    }

    private MockHttpServletRequestBuilder category() {
        return get(PRODUCTS).param("categoryId", leafCategory.toString());
    }

    private JsonNode search(MockHttpServletRequestBuilder request) throws Exception {
        String body =
                mockMvc.perform(request)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }

    private List<UUID> searchIds(MockHttpServletRequestBuilder request) throws Exception {
        List<UUID> ids = new ArrayList<>();
        search(request)
                .get("items")
                .forEach(item -> ids.add(UUID.fromString(item.get("productId").asString())));
        return ids;
    }

    /** 발행자 예시를 읽고 ID, 카테고리, revision을 바꾼다. 필드 구성은 예시 그대로다. */
    private String snapshot(
            String example, UUID productId, long revision, long availabilityRevision)
            throws Exception {
        ObjectNode node =
                (ObjectNode) json.readTree(Files.readString(PUBLISHER_EXAMPLES.resolve(example)));
        node.put("productId", productId.toString());
        node.put("revision", revision);
        node.put("availabilityRevision", availabilityRevision);
        node.put("categoryId", leafCategory.toString());
        ArrayNode path = node.putArray("categoryPath");
        path.add(topCategory.toString());
        path.add(leafCategory.toString());
        return json.writeValueAsString(node);
    }

    private String withIds(String example, UUID productId, Map<String, Object> values)
            throws Exception {
        ObjectNode node =
                (ObjectNode) json.readTree(Files.readString(PUBLISHER_EXAMPLES.resolve(example)));
        node.put("productId", productId.toString());
        values.forEach((field, value) -> node.set(field, json.valueToTree(value)));
        return json.writeValueAsString(node);
    }

    private void send(String topic, String key, String type, String payload) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        record.headers().add("id", utf8(UUID.randomUUID().toString()));
        record.headers().add("type", utf8(type));
        record.headers().add("occurredAt", utf8("2026-10-20T03:04:05.123456Z"));
        record.headers().add("schemaVersion", utf8("1"));
        producer.send(record).get();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    record Fixture(UUID menShirt, UUID womenShirt, UUID earphone) {}
}
