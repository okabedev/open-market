package com.openmarket.promotion;

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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** 구매자 내 쿠폰과 주문서 할인 미리보기(docs/architecture/promotion.md "내 쿠폰", "할인 미리보기"). */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, RedisContainerConfiguration.class})
class BuyerCouponApiIntegrationTest {

    static final String MINE = "/v1/issued-coupons";
    static final String DISCOUNTS = "/v1/coupon-discounts";

    @Autowired MockMvc mockMvc;
    @Autowired CreateCouponUseCase create;
    @Autowired IssueCouponUseCase issue;
    @Autowired JdbcClient jdbc;

    @Test
    @DisplayName("내 쿠폰을 최근 발급 순으로 정책과 화면 상태(쓸 수 있음·주문에 묶임·사용·만료)와 함께 준다")
    void listsMyCoupons() throws Exception {
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID usable = issue(coupon(sellerId, "판매자 3천원", 3_000), buyerId);
        UUID reserved = issue(coupon(null, "플랫폼 천원", 1_000), buyerId);
        UUID used = issue(coupon(null, "지난 쿠폰", 500), buyerId);
        UUID expired = issue(coupon(null, "만료 쿠폰", 2_000), buyerId);
        issue(coupon(null, "남의 쿠폰", 1_000), UUID.randomUUID());
        jdbc.sql(
                        "UPDATE issued_coupon SET status = 'RESERVED', order_id = gen_random_uuid()"
                                + " WHERE id = :id")
                .param("id", reserved)
                .update();
        jdbc.sql(
                        "UPDATE issued_coupon SET status = 'USED', order_id = gen_random_uuid()"
                                + " WHERE id = :id")
                .param("id", used)
                .update();
        jdbc.sql("UPDATE issued_coupon SET expires_at = now() - interval '1 second' WHERE id = :id")
                .param("id", expired)
                .update();

        mockMvc.perform(get(MINE).with(PromotionTokens.buyer(buyerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(4))
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].issuedCouponId").value(expired.toString()))
                .andExpect(jsonPath("$.items[0].state").value("EXPIRED"))
                .andExpect(jsonPath("$.items[1].state").value("USED"))
                .andExpect(jsonPath("$.items[2].state").value("RESERVED"))
                .andExpect(jsonPath("$.items[3].issuedCouponId").value(usable.toString()))
                .andExpect(jsonPath("$.items[3].state").value("USABLE"))
                .andExpect(jsonPath("$.items[3].issuer").value("SELLER"))
                .andExpect(jsonPath("$.items[3].sellerId").value(sellerId.toString()))
                .andExpect(jsonPath("$.items[3].name").value("판매자 3천원"))
                .andExpect(jsonPath("$.items[3].discountType").value("FIXED"))
                .andExpect(jsonPath("$.items[3].discountValue").value(3_000))
                .andExpect(jsonPath("$.items[3].minOrderAmount").value(10_000))
                .andExpect(jsonPath("$.items[3].expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty());

        // 주문서는 지금 쓸 수 있는 쿠폰만 받는다
        mockMvc.perform(get(MINE).param("usable", "true").with(PromotionTokens.buyer(buyerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].issuedCouponId").value(usable.toString()));
    }

    @Test
    @DisplayName("커서로 다음 쪽을 넘기고, 틀린 커서는 400 PROMOTION_INVALID_CURSOR다")
    void pagesByCursor() throws Exception {
        UUID buyerId = UUID.randomUUID();
        UUID first = issue(coupon(null, "하나", 1_000), buyerId);
        UUID second = issue(coupon(null, "둘", 1_000), buyerId);
        UUID third = issue(coupon(null, "셋", 1_000), buyerId);

        String page =
                mockMvc.perform(get(MINE).param("size", "2").with(PromotionTokens.buyer(buyerId)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items[0].issuedCouponId").value(third.toString()))
                        .andExpect(jsonPath("$.items[1].issuedCouponId").value(second.toString()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String cursor = JsonPath.read(page, "$.nextCursor");

        mockMvc.perform(
                        get(MINE)
                                .param("size", "2")
                                .param("cursor", cursor)
                                .with(PromotionTokens.buyer(buyerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].issuedCouponId").value(first.toString()))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        mockMvc.perform(
                        get(MINE)
                                .param("cursor", "not-a-cursor")
                                .with(PromotionTokens.buyer(buyerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROMOTION_INVALID_CURSOR"));
    }

    @Test
    @DisplayName("할인 미리보기는 토큰의 구매자로 주문 생성과 같은 계산을 한다. 남의 쿠폰은 NOT_OWNED다")
    void previewsDiscount() throws Exception {
        UUID buyerId = UUID.randomUUID();
        UUID sellerA = UUID.randomUUID();
        UUID sellerB = UUID.randomUUID();
        UUID sellerCoupon = issue(coupon(sellerA, "판매자", 3_000), buyerId);
        UUID platformCoupon = issue(coupon(null, "플랫폼", 2_700), buyerId);
        UUID othersCoupon = issue(coupon(null, "남의 것", 1_000), UUID.randomUUID());

        mockMvc.perform(
                        post(DISCOUNTS)
                                .with(PromotionTokens.buyer(buyerId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        body(
                                                "\"%s\", \"%s\""
                                                        .formatted(sellerCoupon, platformCoupon),
                                                sellerA,
                                                sellerB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.platformCouponDiscount").value(2_700))
                .andExpect(jsonPath("$.paymentAmount").value(24_300))
                .andExpect(jsonPath("$.sellerOrders[0].sellerCouponDiscount").value(3_000))
                .andExpect(jsonPath("$.sellerOrders[0].paymentAmount").value(15_300));

        mockMvc.perform(
                        post(DISCOUNTS)
                                .with(PromotionTokens.buyer(buyerId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("\"%s\"".formatted(othersCoupon), sellerA, sellerB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(false))
                .andExpect(jsonPath("$.unavailable[0].reason").value("NOT_OWNED"));
    }

    @Test
    @DisplayName("하위 주문이 없거나 판매자가 겹치면 400, 토큰이 없으면 401이다")
    void validatesPreview() throws Exception {
        UUID buyerId = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        mockMvc.perform(
                        post(DISCOUNTS)
                                .with(PromotionTokens.buyer(buyerId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"issuedCouponIds\": [], \"sellerOrders\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROMOTION_INVALID_INPUT"));
        mockMvc.perform(
                        post(DISCOUNTS)
                                .with(PromotionTokens.buyer(buyerId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("", seller, seller)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        post(DISCOUNTS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("", seller, UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(MINE)).andExpect(status().isUnauthorized());
    }

    private static String body(String couponIds, UUID sellerA, UUID sellerB) {
        return """
                {"issuedCouponIds": [%s],
                 "sellerOrders": [{"sellerId": "%s", "amount": 20000}, {"sellerId": "%s", "amount": 10000}]}
                """
                .formatted(couponIds, sellerA, sellerB);
    }

    private UUID coupon(@Nullable UUID sellerId, String name, long fixedDiscount) {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        return create.execute(
                new CreateCouponCommand(
                        UUID.randomUUID(),
                        sellerId,
                        name,
                        CreateCouponCommand.DiscountType.FIXED,
                        fixedDiscount,
                        null,
                        10_000,
                        100,
                        start,
                        start.plus(7, ChronoUnit.DAYS),
                        7));
    }

    private UUID issue(UUID couponId, UUID memberId) {
        return issue.execute(new IssueCouponCommand(memberId, couponId)).issuedCouponId();
    }
}
