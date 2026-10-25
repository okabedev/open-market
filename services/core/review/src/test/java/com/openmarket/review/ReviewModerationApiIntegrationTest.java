package com.openmarket.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.review.command.application.ChangeReviewVisibilityCommand;
import com.openmarket.review.command.application.ChangeReviewVisibilityUseCase;
import com.openmarket.review.command.application.CreateReviewEligibilitiesCommand;
import com.openmarket.review.command.application.CreateReviewEligibilitiesUseCase;
import com.openmarket.review.command.application.WriteReviewCommand;
import com.openmarket.review.command.application.WriteReviewUseCase;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 판매자 답글, 관리자 숨김·보임, 리뷰 목록(docs/architecture/review.md "API"). */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class ReviewModerationApiIntegrationTest {

    static final Instant CONFIRMED_AT = Instant.parse("2026-10-28T07:00:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired CreateReviewEligibilitiesUseCase createEligibilities;
    @Autowired WriteReviewUseCase write;
    @Autowired ChangeReviewVisibilityUseCase visibility;

    final UUID buyerId = UUID.randomUUID();
    final UUID sellerId = UUID.randomUUID();
    final UUID adminId = UUID.randomUUID();
    final UUID productId = UUID.randomUUID();

    UUID reviewId;

    @BeforeEach
    void setUp() {
        reviewId = writeReview(4);
    }

    @Test
    @DisplayName("판매자가 답글을 PUT하면 204이고, 다시 보내면 고친다. 상품 리뷰 목록에 답글이 보인다")
    void replies() throws Exception {
        reply(sellerId, reviewId, "감사합니다").andExpect(status().isNoContent());
        reply(sellerId, reviewId, "  다음에도 이용해 주세요  ").andExpect(status().isNoContent());
        reply(sellerId, reviewId, "  다음에도 이용해 주세요  ").andExpect(status().isNoContent());

        assertThat(
                        jdbc.sql("SELECT content FROM review_reply WHERE review_id = :id")
                                .param("id", reviewId)
                                .query(String.class)
                                .list())
                .containsExactly("다음에도 이용해 주세요");
        mockMvc.perform(get("/v1/products/{productId}/reviews", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].reviewId").value(reviewId.toString()))
                .andExpect(jsonPath("$.items[0].reply.content").value("다음에도 이용해 주세요"))
                .andExpect(jsonPath("$.items[0].buyerId").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    @DisplayName("다른 판매자면 404 REVIEW_NOT_FOUND, 빈 답글은 400 REVIEW_INVALID_INPUT, 판매자가 아니면 403")
    void rejectsReply() throws Exception {
        reply(UUID.randomUUID(), reviewId, "감사합니다")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
        reply(sellerId, reviewId, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVIEW_INVALID_INPUT"));
        mockMvc.perform(
                        put("/v1/seller/reviews/{id}/reply", reviewId)
                                .with(ReviewTokens.buyer(buyerId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"감사합니다\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자가 숨기면 204이고 공개 목록·별점 요약에서 빠지며 이벤트와 감사 로그가 남는다. 다시 숨겨도 그대로다")
    void hides() throws Exception {
        changeVisibility("hide", "  광고성 내용  ").andExpect(status().isNoContent());
        changeVisibility("hide", "광고성 내용").andExpect(status().isNoContent());

        assertThat(statusOf(reviewId)).isEqualTo("HIDDEN");
        assertThat(
                        jdbc.sql(
                                        "SELECT actor_id || '/' || action || '/' || target_type || '/'"
                                                + " || detail FROM audit_log WHERE target_id = :id")
                                .param("id", reviewId.toString())
                                .query(String.class)
                                .list())
                .containsExactly(adminId + "/REVIEW_HIDDEN/REVIEW/광고성 내용");
        assertThat(summaryEvents()).isEqualTo(2);
        mockMvc.perform(get("/v1/products/{productId}/reviews", productId))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(get("/v1/products/{productId}/rating-summary", productId))
                .andExpect(jsonPath("$.ratingCount").value(0));
        mockMvc.perform(get("/v1/reviews").with(ReviewTokens.buyer(buyerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("HIDDEN"));

        changeVisibility("show", "오판이었다").andExpect(status().isNoContent());

        assertThat(statusOf(reviewId)).isEqualTo("VISIBLE");
        assertThat(summaryEvents()).isEqualTo(3);
        mockMvc.perform(get("/v1/products/{productId}/rating-summary", productId))
                .andExpect(jsonPath("$.ratingCount").value(1))
                .andExpect(jsonPath("$.ratingAverage").value(4.0));
    }

    @Test
    @DisplayName("없는 리뷰는 404, 사유가 없으면 400 REVIEW_INVALID_INPUT, 관리자가 아니면 403")
    void rejectsVisibilityChange() throws Exception {
        mockMvc.perform(
                        post("/v1/admin/reviews/{id}/hide", UUID.randomUUID())
                                .with(ReviewTokens.admin(adminId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"광고성 내용\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
        changeVisibility("hide", " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVIEW_INVALID_INPUT"));
        mockMvc.perform(
                        post("/v1/admin/reviews/{id}/hide", reviewId)
                                .with(ReviewTokens.seller(sellerId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"광고성 내용\"}"))
                .andExpect(status().isForbidden());
        assertThat(statusOf(reviewId)).isEqualTo("VISIBLE");
    }

    @Test
    @DisplayName("같은 리뷰를 동시에 여러 번 숨기면 요약에서 한 번만 빠지고 이벤트도 하나다")
    void hidesConcurrentlyOnce() throws Exception {
        UUID other = writeReview(2);
        int threads = 6;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Void> task =
                        () -> {
                            barrier.await(10, TimeUnit.SECONDS);
                            visibility.execute(
                                    new ChangeReviewVisibilityCommand(
                                            adminId, other, false, "광고성 내용"));
                            return null;
                        };
                futures.add(pool.submit(task));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(
                        jdbc.sql(
                                        "SELECT rating_count || '/' || rating_sum || '/' || revision"
                                                + " FROM rating_summary WHERE product_id = :id")
                                .param("id", productId)
                                .query(String.class)
                                .single())
                .isEqualTo("1/4/3");
        assertThat(summaryEvents()).isEqualTo(3);
    }

    @Test
    @DisplayName("목록 API: 쓸 수 있는 리뷰·판매자·관리자 목록과 권한, 틀린 커서는 400, 내 목록은 토큰이 없으면 401")
    void lists() throws Exception {
        UUID writable = eligible();

        mockMvc.perform(get("/v1/reviews/writable").with(ReviewTokens.buyer(buyerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].orderLineId").value(writable.toString()))
                .andExpect(jsonPath("$.items[0].productName").value("반팔 티셔츠"));
        mockMvc.perform(get("/v1/seller/reviews").with(ReviewTokens.seller(sellerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].reviewId").value(reviewId.toString()))
                .andExpect(jsonPath("$.items[0].status").value("VISIBLE"));
        mockMvc.perform(
                        get("/v1/admin/reviews")
                                .param("productId", productId.toString())
                                .param("status", "VISIBLE")
                                .with(ReviewTokens.admin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items[0].rating").value(4));

        mockMvc.perform(get("/v1/admin/reviews").with(ReviewTokens.seller(sellerId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/seller/reviews").with(ReviewTokens.buyer(buyerId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/reviews")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/reviews/writable")).andExpect(status().isUnauthorized());
        mockMvc.perform(
                        get("/v1/products/{productId}/reviews", productId)
                                .param("cursor", "not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVIEW_INVALID_CURSOR"));
    }

    private ResultActions reply(UUID seller, UUID review, String content) throws Exception {
        return mockMvc.perform(
                put("/v1/seller/reviews/{id}/reply", review)
                        .with(ReviewTokens.seller(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"));
    }

    private ResultActions changeVisibility(String action, String reason) throws Exception {
        return mockMvc.perform(
                post("/v1/admin/reviews/{id}/" + action, reviewId)
                        .with(ReviewTokens.admin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + reason + "\"}"));
    }

    private String statusOf(UUID review) {
        return jdbc.sql("SELECT status FROM review WHERE id = :id")
                .param("id", review)
                .query(String.class)
                .single();
    }

    private long summaryEvents() {
        return jdbc.sql(
                        "SELECT count(*) FROM outbox_event WHERE aggregate_type ="
                                + " 'review.rating-summary' AND aggregate_id = :id")
                .param("id", productId.toString())
                .query(Long.class)
                .single();
    }

    private UUID eligible() {
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
                                        line, productId, "반팔 티셔츠", List.of()))));
        return line;
    }

    private UUID writeReview(int rating) {
        return write.execute(
                new WriteReviewCommand(buyerId, eligible(), rating, "사이즈가 잘 맞고 좋아요", List.of()));
    }
}
