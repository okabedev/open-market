package com.openmarket.promotion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.test.container.RedisContainerConfiguration;
import com.openmarket.promotion.command.application.CreateCouponCommand;
import com.openmarket.promotion.command.application.CreateCouponUseCase;
import com.openmarket.promotion.command.application.IssueCouponCommand;
import com.openmarket.promotion.command.application.IssueCouponUseCase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** order가 부르는 할인 계산(내부 API, docs/architecture/promotion.md "할인 계산"). */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, RedisContainerConfiguration.class})
class InternalCouponDiscountApiIntegrationTest {

    static final String DISCOUNTS = "/internal/v1/coupon-discounts";

    @Autowired MockMvc mockMvc;

    @Autowired CreateCouponUseCase create;

    @Autowired IssueCouponUseCase issue;

    @Test
    @DisplayName("판매자 쿠폰과 플랫폼 쿠폰을 하위 주문마다 계산한다")
    void calculates() throws Exception {
        UUID buyerId = UUID.randomUUID();
        UUID sellerA = UUID.randomUUID();
        UUID sellerB = UUID.randomUUID();
        UUID sellerCoupon =
                issue(
                        coupon(sellerA, CreateCouponCommand.DiscountType.FIXED, 3_000, null),
                        buyerId);
        UUID platformCoupon =
                issue(coupon(null, CreateCouponCommand.DiscountType.RATE, 10, 5_000L), buyerId);

        mockMvc.perform(
                        post(DISCOUNTS)
                                .with(PromotionTokens.internal())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        body(
                                                buyerId,
                                                "\"%s\", \"%s\""
                                                        .formatted(sellerCoupon, platformCoupon),
                                                sellerA,
                                                sellerB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.platformIssuedCouponId").value(platformCoupon.toString()))
                .andExpect(jsonPath("$.platformCouponDiscount").value(2_700))
                .andExpect(jsonPath("$.paymentAmount").value(24_300))
                .andExpect(jsonPath("$.sellerOrders[0].sellerId").value(sellerA.toString()))
                .andExpect(
                        jsonPath("$.sellerOrders[0].sellerIssuedCouponId")
                                .value(sellerCoupon.toString()))
                .andExpect(jsonPath("$.sellerOrders[0].sellerCouponDiscount").value(3_000))
                .andExpect(jsonPath("$.sellerOrders[0].platformCouponDiscount").value(1_700))
                .andExpect(jsonPath("$.sellerOrders[0].paymentAmount").value(15_300))
                .andExpect(jsonPath("$.sellerOrders[1].platformCouponDiscount").value(1_000))
                .andExpect(jsonPath("$.unavailable").isEmpty());
    }

    @Test
    @DisplayName("쓸 수 없는 쿠폰은 200에 applicable=false와 쿠폰마다 이유를 준다")
    void unavailable() throws Exception {
        UUID platformCoupon =
                issue(
                        coupon(null, CreateCouponCommand.DiscountType.FIXED, 1_000, null),
                        UUID.randomUUID());

        mockMvc.perform(
                        post(DISCOUNTS)
                                .with(PromotionTokens.internal())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        body(
                                                UUID.randomUUID(),
                                                "\"%s\"".formatted(platformCoupon),
                                                UUID.randomUUID(),
                                                UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(false))
                .andExpect(
                        jsonPath("$.unavailable[0].issuedCouponId")
                                .value(platformCoupon.toString()))
                .andExpect(jsonPath("$.unavailable[0].reason").value("NOT_OWNED"));
    }

    @Test
    @DisplayName("서비스 간 토큰만 부른다. 구매자 토큰은 403, 토큰이 없으면 401")
    void internalOnly() throws Exception {
        String body = body(UUID.randomUUID(), "", UUID.randomUUID(), UUID.randomUUID());
        mockMvc.perform(
                        post(DISCOUNTS)
                                .with(PromotionTokens.buyer(UUID.randomUUID()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(DISCOUNTS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    private static String body(UUID buyerId, String couponIds, UUID sellerA, UUID sellerB) {
        return """
                {"buyerId": "%s", "issuedCouponIds": [%s],
                 "sellerOrders": [{"sellerId": "%s", "amount": 20000}, {"sellerId": "%s", "amount": 10000}]}
                """
                .formatted(buyerId, couponIds, sellerA, sellerB);
    }

    private UUID coupon(
            @Nullable UUID sellerId,
            CreateCouponCommand.DiscountType type,
            long value,
            @Nullable Long maxDiscount) {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        return create.execute(
                new CreateCouponCommand(
                        UUID.randomUUID(),
                        sellerId,
                        "쿠폰",
                        type,
                        value,
                        maxDiscount,
                        0,
                        100,
                        start,
                        start.plus(7, ChronoUnit.DAYS),
                        7));
    }

    private UUID issue(UUID couponId, UUID memberId) {
        return issue.execute(new IssueCouponCommand(memberId, couponId)).issuedCouponId();
    }
}
