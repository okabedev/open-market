package com.openmarket.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.review.command.application.CreateReviewEligibilitiesCommand;
import com.openmarket.review.command.application.CreateReviewEligibilitiesUseCase;
import com.openmarket.review.command.application.WriteReviewCommand;
import com.openmarket.review.command.application.WriteReviewUseCase;
import com.openmarket.review.command.domain.ReviewAlreadyWrittenException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 리뷰 작성과 별점 요약 조회(docs/architecture/review.md "API"). */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class ReviewApiIntegrationTest {

    static final Instant CONFIRMED_AT = Instant.parse("2026-10-28T07:00:00Z");

    @Autowired MockMvc mockMvc;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired CreateReviewEligibilitiesUseCase createEligibilities;

    @Autowired WriteReviewUseCase write;

    final UUID buyerId = UUID.randomUUID();
    final UUID sellerId = UUID.randomUUID();
    final UUID productId = UUID.randomUUID();

    @Test
    @DisplayName("구매확정한 주문 항목에 리뷰를 쓰면 201이고, 별점 요약과 두 이벤트가 같은 트랜잭션에 남는다")
    void writes() throws Exception {
        UUID line = eligible(List.of("화이트", "M"));
        UUID file = UUID.randomUUID();

        String reviewId =
                JsonPath.read(
                        mockMvc.perform(
                                        post("/v1/reviews")
                                                .with(ReviewTokens.buyer(buyerId))
                                                .header(
                                                        "Idempotency-Key",
                                                        UUID.randomUUID().toString())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        body(
                                                                line,
                                                                5,
                                                                "  사이즈가 잘 맞고 좋아요  ",
                                                                "[\"" + file + "\"]")))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "$.reviewId");

        assertThat(
                        jdbc.sql(
                                        "SELECT rating || '/' || content || '/' || status || '/' ||"
                                                + " option_value_1 || '/' || option_value_2 FROM review"
                                                + " WHERE id = :id")
                                .param("id", UUID.fromString(reviewId))
                                .query(String.class)
                                .single())
                .isEqualTo("5/사이즈가 잘 맞고 좋아요/VISIBLE/화이트/M");
        assertThat(
                        jdbc.sql("SELECT file_id FROM review_file WHERE review_id = :id")
                                .param("id", UUID.fromString(reviewId))
                                .query(UUID.class)
                                .list())
                .containsExactly(file);
        assertThat(
                        jdbc.sql(
                                        "SELECT review_id FROM review_eligibility WHERE order_line_id ="
                                                + " :id")
                                .param("id", line)
                                .query(UUID.class)
                                .single())
                .isEqualTo(UUID.fromString(reviewId));

        JsonNode created = json.readTree(outboxPayload("review.review", reviewId));
        assertThat(created.propertyNames()).isEqualTo(example("ReviewCreated").propertyNames());
        assertThat(created.get("orderLineId").asString()).isEqualTo(line.toString());
        assertThat(created.get("rating").asInt()).isEqualTo(5);
        assertThat(created.get("fileIds").get(0).asString()).isEqualTo(file.toString());

        JsonNode changed =
                json.readTree(outboxPayload("review.rating-summary", productId.toString()));
        assertThat(changed.propertyNames())
                .isEqualTo(example("RatingSummaryChanged").propertyNames());
        assertThat(changed.get("ratingCount").asLong()).isEqualTo(1);
        assertThat(changed.get("ratingSum").asLong()).isEqualTo(5);
        assertThat(changed.get("ratingAverage").decimalValue()).isEqualByComparingTo("5.0");
        assertThat(changed.get("revision").asLong()).isEqualTo(1);

        mockMvc.perform(get("/v1/products/{productId}/rating-summary", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.ratingCount").value(1))
                .andExpect(jsonPath("$.ratingAverage").value(5.0));
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 다시 보내면 같은 리뷰이고, 다른 키로 다시 쓰면 409 REVIEW_ALREADY_WRITTEN")
    void writesOnce() throws Exception {
        UUID line = eligible(List.of());
        String key = UUID.randomUUID().toString();
        String body = body(line, 4, "배송이 빠르고 좋아요", null);

        String first =
                writeWith(key, body)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String again =
                writeWith(key, body)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        writeWith(UUID.randomUUID().toString(), body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_WRITTEN"));

        assertThat(JsonPath.<String>read(again, "$.reviewId"))
                .isEqualTo(JsonPath.<String>read(first, "$.reviewId"));
        assertThat(
                        jdbc.sql("SELECT count(*) FROM review WHERE order_line_id = :id")
                                .param("id", line)
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);
        assertThat(summaryRevision()).isEqualTo(1);
    }

    @Test
    @DisplayName("작성 자격이 없거나 남의 것이면 404 REVIEW_NOT_ELIGIBLE")
    void rejectsWithoutEligibility() throws Exception {
        UUID line = eligible(List.of());

        writeWith(UUID.randomUUID().toString(), body(UUID.randomUUID(), 5, "사이즈가 잘 맞고 좋아요", null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ELIGIBLE"));
        mockMvc.perform(
                        post("/v1/reviews")
                                .with(ReviewTokens.buyer(UUID.randomUUID()))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(line, 5, "사이즈가 잘 맞고 좋아요", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ELIGIBLE"));
    }

    @Test
    @DisplayName(
            "Idempotency-Key가 없으면 400 COMMON_IDEMPOTENCY_KEY_REQUIRED, 입력 규칙 위반은 400 REVIEW_INVALID_INPUT, 토큰이 없으면 401")
    void validates() throws Exception {
        UUID line = eligible(List.of());

        mockMvc.perform(
                        post("/v1/reviews")
                                .with(ReviewTokens.buyer(buyerId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(line, 5, "사이즈가 잘 맞고 좋아요", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_IDEMPOTENCY_KEY_REQUIRED"));
        writeWith(UUID.randomUUID().toString(), body(line, 6, "사이즈가 잘 맞고 좋아요", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVIEW_INVALID_INPUT"));
        writeWith(UUID.randomUUID().toString(), body(line, 5, "짧아요", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVIEW_INVALID_INPUT"));
        mockMvc.perform(
                        post("/v1/reviews")
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(line, 5, "사이즈가 잘 맞고 좋아요", null)))
                .andExpect(status().isUnauthorized());
        assertThat(
                        jdbc.sql(
                                        "SELECT review_id FROM review_eligibility WHERE order_line_id = :id")
                                .param("id", line)
                                .query(UUID.class)
                                .optional())
                .isEmpty();
    }

    @Test
    @DisplayName("리뷰가 없는 상품의 별점 요약은 토큰 없이 리뷰 수 0, 평균 없음이다")
    void emptySummary() throws Exception {
        mockMvc.perform(get("/v1/products/{productId}/rating-summary", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingCount").value(0))
                .andExpect(jsonPath("$.ratingAverage").doesNotExist());
    }

    @Test
    @DisplayName("같은 상품의 리뷰가 동시에 와도 별점 요약은 모두 세고 리비전이 겹치지 않는다")
    void concurrentReviewsOfSameProduct() throws Exception {
        int threads = 8;
        List<UUID> lines = IntStream.range(0, threads).mapToObj(i -> eligible(List.of())).toList();
        CyclicBarrier start = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<UUID>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int rating = i % 5 + 1;
                UUID line = lines.get(i);
                Callable<UUID> task =
                        () -> {
                            start.await(10, TimeUnit.SECONDS);
                            return write.execute(
                                    new WriteReviewCommand(
                                            buyerId, line, rating, "동시에 쓰는 리뷰입니다", List.of()));
                        };
                results.add(pool.submit(task));
            }
            for (Future<UUID> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // 별점 1,2,3,4,5,1,2,3 → 합 21
        assertThat(
                        jdbc.sql(
                                        "SELECT rating_count || '/' || rating_sum || '/' || revision"
                                                + " FROM rating_summary WHERE product_id = :id")
                                .param("id", productId)
                                .query(String.class)
                                .single())
                .isEqualTo("8/21/8");
        assertThat(
                        jdbc.sql(
                                        "SELECT (payload->>'revision')::bigint FROM outbox_event"
                                                + " WHERE aggregate_type = 'review.rating-summary' AND"
                                                + " aggregate_id = :id ORDER BY 1")
                                .param("id", productId.toString())
                                .query(Long.class)
                                .list())
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    }

    @Test
    @DisplayName("같은 주문 항목의 리뷰가 동시에 와도 하나만 쓴다")
    void concurrentReviewsOfSameLine() throws Exception {
        UUID line = eligible(List.of());
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        int written = 0;
        int rejected = 0;
        try {
            List<Future<UUID>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Callable<UUID> task =
                        () -> {
                            start.await(10, TimeUnit.SECONDS);
                            return write.execute(
                                    new WriteReviewCommand(
                                            buyerId, line, 5, "동시에 쓰는 리뷰입니다", List.of()));
                        };
                results.add(pool.submit(task));
            }
            for (Future<UUID> result : results) {
                try {
                    result.get(30, TimeUnit.SECONDS);
                    written++;
                } catch (java.util.concurrent.ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(ReviewAlreadyWrittenException.class);
                    rejected++;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(written).isEqualTo(1);
        assertThat(rejected).isEqualTo(1);
        assertThat(summaryRevision()).isEqualTo(1);
    }

    private UUID eligible(List<String> options) {
        UUID line = UUID.randomUUID();
        createEligibilities.execute(
                new CreateReviewEligibilitiesCommand(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        sellerId,
                        buyerId,
                        CONFIRMED_AT,
                        List.of(
                                new CreateReviewEligibilitiesCommand.Line(
                                        line, productId, "반팔 티셔츠", options))));
        return line;
    }

    private org.springframework.test.web.servlet.ResultActions writeWith(String key, String body)
            throws Exception {
        return mockMvc.perform(
                post("/v1/reviews")
                        .with(ReviewTokens.buyer(buyerId))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private static String body(UUID line, int rating, String content, @Nullable String fileIds) {
        return """
                {"orderLineId":"%s","rating":%d,"content":"%s"%s}"""
                .formatted(line, rating, content, fileIds == null ? "" : ",\"fileIds\":" + fileIds);
    }

    private long summaryRevision() {
        return jdbc.sql("SELECT revision FROM rating_summary WHERE product_id = :id")
                .param("id", productId)
                .query(Long.class)
                .single();
    }

    private String outboxPayload(String topic, String aggregateId) {
        return jdbc.sql(
                        "SELECT payload::text FROM outbox_event WHERE aggregate_type = :topic AND"
                                + " aggregate_id = :id")
                .param("topic", topic)
                .param("id", aggregateId)
                .query(String.class)
                .single();
    }

    private JsonNode example(String type) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/events/" + type + ".json")) {
            return json.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
