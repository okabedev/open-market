package com.openmarket.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 쿠폰 정책 생성·중지·조회(docs/architecture/promotion.md "API"). */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class CouponApiIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Autowired JdbcClient jdbc;

    final Instant start = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
    final Instant end = start.plus(7, ChronoUnit.DAYS);

    @Test
    @DisplayName("관리자가 플랫폼 쿠폰을 만들면 201과 couponId를 주고 감사 로그를 남긴다")
    void adminCreates() throws Exception {
        UUID adminId = UUID.randomUUID();

        String couponId = create("/v1/admin/coupons", PromotionTokens.admin(adminId), rateBody());

        mockMvc.perform(
                        get("/v1/admin/coupons")
                                .param("issuer", "PLATFORM")
                                .param("size", "100")
                                .with(PromotionTokens.admin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].couponId", hasItem(couponId)));
        mockMvc.perform(get("/v1/coupons/{couponId}", couponId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("PLATFORM"))
                .andExpect(jsonPath("$.sellerId").doesNotExist())
                .andExpect(jsonPath("$.name").value("가을 맞이 10%"))
                .andExpect(jsonPath("$.discountType").value("RATE"))
                .andExpect(jsonPath("$.discountValue").value(10))
                .andExpect(jsonPath("$.maxDiscount").value(5000))
                .andExpect(jsonPath("$.minOrderAmount").value(20000))
                .andExpect(jsonPath("$.quantity").value(100))
                .andExpect(jsonPath("$.issueStartAt").value(start.toString()))
                .andExpect(jsonPath("$.issueEndAt").value(end.toString()))
                .andExpect(jsonPath("$.validDays").value(14))
                .andExpect(jsonPath("$.status").value("ISSUING"));
        assertThat(
                        jdbc.sql(
                                        "SELECT actor_id || '/' || action || '/' || target_type FROM"
                                                + " audit_log WHERE target_id = :id")
                                .param("id", couponId)
                                .query(String.class)
                                .list())
                .containsExactly(adminId + "/COUPON_CREATED/COUPON");
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 다시 만들면 쿠폰은 하나다")
    void idempotent() throws Exception {
        UUID sellerId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();

        String first = createWithKey(PromotionTokens.seller(sellerId), key);
        String second = createWithKey(PromotionTokens.seller(sellerId), key);

        assertThat(second).isEqualTo(first);
        mockMvc.perform(get("/v1/seller/coupons").with(PromotionTokens.seller(sellerId)))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    @DisplayName(
            "Idempotency-Key가 없으면 400 COMMON_IDEMPOTENCY_KEY_REQUIRED, 입력 규칙 위반은 400 PROMOTION_INVALID_INPUT")
    void invalid() throws Exception {
        mockMvc.perform(
                        post("/v1/seller/coupons")
                                .with(PromotionTokens.seller(UUID.randomUUID()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(rateBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_IDEMPOTENCY_KEY_REQUIRED"));
        mockMvc.perform(
                        post("/v1/seller/coupons")
                                .with(PromotionTokens.seller(UUID.randomUUID()))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"name": "정액", "discountType": "FIXED", "discountValue": 1000,
                                         "maxDiscount": 500, "minOrderAmount": 0, "quantity": 10,
                                         "issueStartAt": "%s", "issueEndAt": "%s", "validDays": 7}
                                        """
                                                .formatted(start, end)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROMOTION_INVALID_INPUT"));
        mockMvc.perform(
                        post("/v1/seller/coupons")
                                .with(PromotionTokens.seller(UUID.randomUUID()))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\": \"빈 쿠폰\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    @DisplayName("판매자는 자기 쿠폰만 보고 중지한다. 중지는 다시 해도 204다")
    void sellerStops() throws Exception {
        UUID sellerId = UUID.randomUUID();
        String own = create("/v1/seller/coupons", PromotionTokens.seller(sellerId), rateBody());
        String others =
                create("/v1/seller/coupons", PromotionTokens.seller(UUID.randomUUID()), rateBody());

        mockMvc.perform(
                        post("/v1/seller/coupons/{couponId}/stop", own)
                                .with(PromotionTokens.seller(sellerId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(
                        post("/v1/seller/coupons/{couponId}/stop", own)
                                .with(PromotionTokens.seller(sellerId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(
                        post("/v1/seller/coupons/{couponId}/stop", others)
                                .with(PromotionTokens.seller(sellerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_COUPON_NOT_FOUND"));

        mockMvc.perform(get("/v1/seller/coupons").with(PromotionTokens.seller(sellerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items[0].couponId").value(own))
                .andExpect(jsonPath("$.items[0].issuer").value("SELLER"))
                .andExpect(jsonPath("$.items[0].sellerId").value(sellerId.toString()))
                .andExpect(jsonPath("$.items[0].status").value("STOPPED"))
                .andExpect(jsonPath("$.items[0].stoppedAt").isNotEmpty());
        mockMvc.perform(
                        get("/v1/seller/coupons")
                                .param("status", "ISSUING")
                                .with(PromotionTokens.seller(sellerId)))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("관리자는 판매자 쿠폰도 중지하고, 판매자·상태로 거른다")
    void adminStopsSellerCoupon() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        String couponId =
                create("/v1/seller/coupons", PromotionTokens.seller(sellerId), fixedBody());

        mockMvc.perform(
                        post("/v1/admin/coupons/{couponId}/stop", couponId)
                                .with(PromotionTokens.admin(adminId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(
                        post("/v1/admin/coupons/{couponId}/stop", UUID.randomUUID())
                                .with(PromotionTokens.admin(adminId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_COUPON_NOT_FOUND"));

        mockMvc.perform(
                        get("/v1/admin/coupons")
                                .param("sellerId", sellerId.toString())
                                .param("status", "STOPPED")
                                .with(PromotionTokens.admin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].couponId").value(couponId))
                .andExpect(jsonPath("$.items[0].discountType").value("FIXED"))
                .andExpect(jsonPath("$.items[0].maxDiscount").doesNotExist());
        assertThat(
                        jdbc.sql("SELECT action FROM audit_log WHERE target_id = :id")
                                .param("id", couponId)
                                .query(String.class)
                                .list())
                .containsExactly("COUPON_STOPPED");
    }

    @Test
    @DisplayName("공개 목록은 지금 내려받을 수 있는 쿠폰만 준다. sellerId가 없으면 플랫폼 쿠폰, 있으면 그 판매자 쿠폰")
    void issuableList() throws Exception {
        UUID sellerId = UUID.randomUUID();
        String issuing = create("/v1/seller/coupons", PromotionTokens.seller(sellerId), rateBody());
        String stopped = create("/v1/seller/coupons", PromotionTokens.seller(sellerId), rateBody());
        Instant later = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String notStarted =
                create(
                        "/v1/seller/coupons",
                        PromotionTokens.seller(sellerId),
                        body("FIXED", 1000, null, later, later.plus(1, ChronoUnit.DAYS)));
        String platform =
                create("/v1/admin/coupons", PromotionTokens.admin(UUID.randomUUID()), rateBody());
        mockMvc.perform(
                        post("/v1/seller/coupons/{couponId}/stop", stopped)
                                .with(PromotionTokens.seller(sellerId)))
                .andExpect(status().isNoContent());

        String sellerList =
                mockMvc.perform(get("/v1/coupons").param("sellerId", sellerId.toString()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<String> ids = JsonPath.read(sellerList, "$.items[*].couponId");
        assertThat(ids).containsExactly(issuing);
        assertThat(List.of(stopped, notStarted)).doesNotContainAnyElementsOf(ids);

        mockMvc.perform(get("/v1/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].couponId", hasItem(platform)))
                .andExpect(jsonPath("$.items[*].couponId", not(hasItem(issuing))))
                .andExpect(jsonPath("$.items[*].issuer", not(hasItem("SELLER"))));
        mockMvc.perform(get("/v1/coupons/{couponId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_COUPON_NOT_FOUND"));
    }

    @Test
    @DisplayName("판매자 경로는 SELLER, 관리자 경로는 ADMIN이 필요하다. 토큰이 없으면 401")
    void roles() throws Exception {
        UUID buyerId = UUID.randomUUID();
        mockMvc.perform(get("/v1/seller/coupons").with(PromotionTokens.buyer(buyerId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/admin/coupons").with(PromotionTokens.seller(buyerId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/admin/coupons")).andExpect(status().isUnauthorized());
        mockMvc.perform(
                        post("/v1/admin/coupons/{couponId}/stop", UUID.randomUUID())
                                .with(PromotionTokens.seller(buyerId)))
                .andExpect(status().isForbidden());
    }

    private String create(String path, RequestPostProcessor token, String body) throws Exception {
        String response =
                mockMvc.perform(
                                post(path)
                                        .with(token)
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.read(response, "$.couponId");
    }

    private String createWithKey(RequestPostProcessor token, String key) throws Exception {
        String response =
                mockMvc.perform(
                                post("/v1/seller/coupons")
                                        .with(token)
                                        .header("Idempotency-Key", key)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(rateBody()))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.read(response, "$.couponId");
    }

    private String rateBody() {
        return body("RATE", 10, 5000L, start, end);
    }

    private String fixedBody() {
        return body("FIXED", 3000, null, start, end);
    }

    private static String body(
            String discountType,
            long discountValue,
            @org.jspecify.annotations.Nullable Long maxDiscount,
            Instant issueStartAt,
            Instant issueEndAt) {
        return """
                {"name": "%s", "discountType": "%s", "discountValue": %d, "maxDiscount": %s,
                 "minOrderAmount": 20000, "quantity": 100,
                 "issueStartAt": "%s", "issueEndAt": "%s", "validDays": 14}
                """
                .formatted(
                        discountType.equals("RATE") ? "가을 맞이 10%" : "가을 맞이 3천 원",
                        discountType,
                        discountValue,
                        maxDiscount == null ? "null" : maxDiscount.toString(),
                        issueStartAt,
                        issueEndAt);
    }
}
