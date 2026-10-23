package com.openmarket.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.test.container.RedisContainerConfiguration;
import com.openmarket.promotion.command.application.CreateCouponCommand;
import com.openmarket.promotion.command.application.CreateCouponUseCase;
import com.openmarket.promotion.command.application.IssueCouponCommand;
import com.openmarket.promotion.command.application.IssueCouponUseCase;
import com.openmarket.promotion.command.application.StopCouponCommand;
import com.openmarket.promotion.command.application.StopCouponUseCase;
import com.openmarket.promotion.command.domain.CouponSoldOutException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** 선착순 발급(docs/architecture/promotion.md "선착순 발급")과 R11 정합성(docs/architecture/risks.md). */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, RedisContainerConfiguration.class})
class IssueCouponApiIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Autowired CreateCouponUseCase create;

    @Autowired StopCouponUseCase stop;

    @Autowired IssueCouponUseCase issue;

    @Autowired StringRedisTemplate redis;

    @Autowired JdbcClient jdbc;

    @Test
    @DisplayName("처음 내려받으면 201, 다시 누르면 200과 같은 issuedCouponId. 쿠폰 조회에 발급 수가 보인다")
    void issueOnce() throws Exception {
        UUID couponId = coupon(10);
        UUID memberId = UUID.randomUUID();

        String first =
                mockMvc.perform(
                                post("/v1/coupons/{couponId}/issue", couponId)
                                        .with(PromotionTokens.buyer(memberId)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String second =
                mockMvc.perform(
                                post("/v1/coupons/{couponId}/issue", couponId)
                                        .with(PromotionTokens.buyer(memberId)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String issuedCouponId = JsonPath.read(first, "$.issuedCouponId");
        assertThat((String) JsonPath.read(second, "$.issuedCouponId")).isEqualTo(issuedCouponId);
        mockMvc.perform(get("/v1/coupons/{couponId}", couponId))
                .andExpect(jsonPath("$.issuedCount").value(1));
        assertThat(
                        jdbc.sql(
                                        "SELECT status || '/' || (expires_at - issued_at) FROM"
                                                + " issued_coupon WHERE id = :id")
                                .param("id", UUID.fromString(issuedCouponId))
                                .query(String.class)
                                .single())
                .isEqualTo("AVAILABLE/7 days");
    }

    @Test
    @DisplayName(
            "다 나가면 409 PROMOTION_COUPON_SOLD_OUT, 중지한 쿠폰은 409 PROMOTION_COUPON_NOT_ISSUING, 없는 쿠폰은 404")
    void conflicts() throws Exception {
        UUID couponId = coupon(1);
        issue.execute(new IssueCouponCommand(UUID.randomUUID(), couponId));

        mockMvc.perform(
                        post("/v1/coupons/{couponId}/issue", couponId)
                                .with(PromotionTokens.buyer(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMOTION_COUPON_SOLD_OUT"));

        UUID stopped = coupon(10);
        stop.execute(new StopCouponCommand(UUID.randomUUID(), null, stopped));
        mockMvc.perform(
                        post("/v1/coupons/{couponId}/issue", stopped)
                                .with(PromotionTokens.buyer(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMOTION_COUPON_NOT_ISSUING"));

        mockMvc.perform(
                        post("/v1/coupons/{couponId}/issue", UUID.randomUUID())
                                .with(PromotionTokens.buyer(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_COUPON_NOT_FOUND"));
        mockMvc.perform(post("/v1/coupons/{couponId}/issue", couponId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("R11: 수량보다 많은 회원이 두 번씩 동시에 누르고 그사이 Redis 키를 지워도 DB 발급 수는 수량과 같고 회원당 1장이다")
    void concurrentIssueKeepsQuantityAndOnePerMember() throws Exception {
        int quantity = 30;
        int members = 120;
        UUID couponId = coupon(quantity);
        List<UUID> memberIds = new ArrayList<>();
        for (int i = 0; i < members; i++) {
            memberIds.add(UUID.randomUUID());
        }
        Map<UUID, Set<UUID>> issuedIdsByMember = new ConcurrentHashMap<>();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch start = new CountDownLatch(1);

        // Redis 재시작처럼 발급이 시작된 뒤 키를 여러 번 지운다. 계속 지우면 키를 만들 틈이 없어 503만 나므로 횟수를 둔다.
        AtomicInteger flushes = new AtomicInteger();
        Thread flusher =
                Thread.ofPlatform()
                        .start(
                                () -> {
                                    awaitQuietly(start);
                                    while (running.get() && flushes.get() < 20) {
                                        redis.delete(
                                                List.of(
                                                        "promotion:coupon-stock:" + couponId,
                                                        "promotion:coupon-issued:" + couponId));
                                        flushes.incrementAndGet();
                                        sleepQuietly(10);
                                    }
                                });
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(32)) {
            for (UUID memberId : memberIds) {
                for (int attempt = 0; attempt < 2; attempt++) {
                    futures.add(
                            pool.submit(
                                    () -> {
                                        start.await();
                                        try {
                                            IssueCouponUseCase.Result result =
                                                    issue.execute(
                                                            new IssueCouponCommand(
                                                                    memberId, couponId));
                                            issuedIdsByMember
                                                    .computeIfAbsent(
                                                            memberId,
                                                            id -> ConcurrentHashMap.newKeySet())
                                                    .add(result.issuedCouponId());
                                        } catch (CouponSoldOutException e) {
                                            soldOut.incrementAndGet();
                                        }
                                        return null;
                                    }));
                }
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            running.set(false);
            flusher.join();
        }

        long rows =
                jdbc.sql("SELECT count(*) FROM issued_coupon WHERE coupon_id = :id")
                        .param("id", couponId)
                        .query(Long.class)
                        .single();
        long distinctMembers =
                jdbc.sql(
                                "SELECT count(DISTINCT member_id) FROM issued_coupon WHERE"
                                        + " coupon_id = :id")
                        .param("id", couponId)
                        .query(Long.class)
                        .single();
        int issuedCount =
                jdbc.sql("SELECT issued_count FROM coupon WHERE id = :id")
                        .param("id", couponId)
                        .query(Integer.class)
                        .single();
        assertThat(rows).isEqualTo(quantity);
        assertThat(distinctMembers).isEqualTo(rows);
        assertThat(issuedCount).isEqualTo(quantity);
        // 같은 회원의 두 요청은 같은 발급 쿠폰을 받는다.
        assertThat(issuedIdsByMember).hasSize(quantity);
        assertThat(issuedIdsByMember.values()).allSatisfy(ids -> assertThat(ids).hasSize(1));
        assertThat(soldOut.get()).isPositive();
        assertThat(flushes.get()).isPositive();
    }

    private UUID coupon(int quantity) {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        return create.execute(
                new CreateCouponCommand(
                        UUID.randomUUID(),
                        null,
                        "선착순 쿠폰",
                        CreateCouponCommand.DiscountType.FIXED,
                        3_000,
                        null,
                        0,
                        quantity,
                        start,
                        start.plus(7, ChronoUnit.DAYS),
                        7));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
