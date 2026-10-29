package com.openmarket.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.settlement.command.application.CreateSettlementItemCommand;
import com.openmarket.settlement.command.application.CreateSettlementItemUseCase;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 정산 즉시 실행, 판매자·관리자 조회, 지급 처리(docs/architecture/settlement.md "API"). */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class SettlementApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired JsonMapper json;
    @Autowired CreateSettlementItemUseCase createItem;

    final UUID sellerId = UUID.randomUUID();
    final UUID adminId = UUID.randomUUID();
    final Instant twoWeeksAgo = Instant.now().minus(Duration.ofDays(14));

    @Test
    @DisplayName("관리자가 정산 작업을 돌리면 확정한 수를 주고, 다시 돌려도 이 판매자의 정산은 그대로다. 판매자는 정산과 내역을 본다")
    void runsAndShowsSettlements() throws Exception {
        purchase(twoWeeksAgo, 30_000, 2_000);
        purchase(twoWeeksAgo, 10_000, 0);
        UUID pending = purchase(Instant.now(), 5_000, 0);

        String run =
                mockMvc.perform(post("/v1/admin/settlement-runs").with(admin()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(JsonPath.<Integer>read(run, "$.confirmedCount")).isPositive();
        mockMvc.perform(post("/v1/admin/settlement-runs").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedCount").value(0));

        String list =
                mockMvc.perform(get("/v1/seller/settlements").with(seller(sellerId)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalCount").value(1))
                        .andExpect(jsonPath("$.items[0].itemCount").value(2))
                        .andExpect(jsonPath("$.items[0].salesAmount").value(40_000))
                        .andExpect(jsonPath("$.items[0].commissionAmount").value(4_000))
                        .andExpect(jsonPath("$.items[0].sellerCouponDiscount").value(2_000))
                        .andExpect(jsonPath("$.items[0].amount").value(34_000))
                        .andExpect(jsonPath("$.items[0].status").value("CONFIRMED"))
                        .andExpect(jsonPath("$.items[0].paidOutAt").isEmpty())
                        .andExpect(jsonPath("$.nextCursor").isEmpty())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String settlementId = JsonPath.read(list, "$.items[0].settlementId");

        mockMvc.perform(
                        get("/v1/seller/settlements/{id}/items", settlementId)
                                .param("size", "1")
                                .with(seller(sellerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].lines[0].productName").value("반팔 티셔츠"))
                .andExpect(jsonPath("$.items[0].lines[0].commissionRateBp").value(1000))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty());
        mockMvc.perform(get("/v1/seller/settlement-items/pending").with(seller(sellerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].sellerOrderId").value(pending.toString()));
        mockMvc.perform(get("/v1/seller/settlement-items/pending/summary").with(seller(sellerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.salesAmount").value(5_000))
                .andExpect(jsonPath("$.settlementAmount").value(4_500));
        mockMvc.perform(
                        get("/v1/seller/settlement-items/pending/summary")
                                .with(seller(UUID.randomUUID())))
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.settlementAmount").value(0));

        // 다른 판매자에게는 없는 정산이다
        mockMvc.perform(
                        get("/v1/seller/settlements/{id}", settlementId)
                                .with(seller(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
        mockMvc.perform(
                        get("/v1/seller/settlements/{id}/items", settlementId)
                                .with(seller(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("관리자가 지급 처리하면 PAID_OUT이고 이벤트와 감사 로그가 남는다. 다시 해도 204이고 그대로다")
    void paysOut() throws Exception {
        purchase(twoWeeksAgo, 20_000, 0);
        mockMvc.perform(post("/v1/admin/settlement-runs").with(admin())).andExpect(status().isOk());
        String settlementId =
                jdbc.sql("SELECT id::text FROM settlement WHERE seller_id = :id")
                        .param("id", sellerId)
                        .query(String.class)
                        .single();

        mockMvc.perform(post("/v1/admin/settlements/{id}/payout", settlementId).with(admin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/v1/admin/settlements/{id}/payout", settlementId).with(admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/v1/admin/settlements")
                                .param("status", "PAID_OUT")
                                .param("sellerId", sellerId.toString())
                                .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].status").value("PAID_OUT"))
                .andExpect(jsonPath("$.items[0].paidOutAt").isNotEmpty());
        assertThat(
                        jdbc.sql(
                                        "SELECT actor_id || '/' || action || '/' || target_type || '/'"
                                                + " || detail FROM audit_log WHERE target_id = :id"
                                                + " ORDER BY occurred_at, id")
                                .param("id", settlementId)
                                .query(String.class)
                                .list())
                .satisfiesExactly(
                        confirmed ->
                                assertThat(confirmed)
                                        .startsWith(
                                                adminId + "/SETTLEMENT_CONFIRMED/SETTLEMENT/실행 ")
                                        .endsWith(", 18000원"),
                        paid ->
                                assertThat(paid)
                                        .isEqualTo(
                                                adminId
                                                        + "/SETTLEMENT_PAID_OUT/SETTLEMENT/18000원"));
        List<String> paidOut =
                jdbc.sql(
                                "SELECT payload::text FROM outbox_event WHERE aggregate_id = :id AND"
                                        + " type = 'SettlementPaidOut'")
                        .param("id", settlementId)
                        .query(String.class)
                        .list();
        assertThat(paidOut).hasSize(1);
        JsonNode payload = json.readTree(paidOut.getFirst());
        assertThat(payload.propertyNames()).isEqualTo(example("SettlementPaidOut").propertyNames());
        assertThat(payload.get("amount").asLong()).isEqualTo(18_000);
        assertThat(
                        jdbc.sql("SELECT paid_out_by FROM settlement WHERE id = :id::uuid")
                                .param("id", settlementId)
                                .query(UUID.class)
                                .single())
                .isEqualTo(adminId);
    }

    @Test
    @DisplayName("없는 정산은 404, 판매자가 관리자 API를 부르면 403, 토큰이 없으면 401, 틀린 커서는 400")
    void rejects() throws Exception {
        mockMvc.perform(post("/v1/admin/settlements/{id}/payout", UUID.randomUUID()).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
        mockMvc.perform(get("/v1/admin/settlements/{id}", UUID.randomUUID()).with(admin()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/v1/admin/settlement-runs").with(seller(sellerId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/seller/settlements")).andExpect(status().isUnauthorized());
        mockMvc.perform(
                        get("/v1/seller/settlements")
                                .param("cursor", "not-a-cursor")
                                .with(seller(sellerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_INVALID_CURSOR"));
    }

    private UUID purchase(Instant confirmedAt, long amount, long couponDiscount) {
        UUID sellerOrderId = UUID.randomUUID();
        createItem.execute(
                new CreateSettlementItemCommand(
                        UUID.randomUUID(),
                        sellerOrderId,
                        sellerId,
                        confirmedAt,
                        couponDiscount,
                        List.of(
                                new CreateSettlementItemCommand.Line(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        "반팔 티셔츠",
                                        UUID.randomUUID(),
                                        1,
                                        amount))));
        return sellerOrderId;
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(
                        token -> token.subject(adminId.toString()).claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor seller(UUID sellerId) {
        return jwt().jwt(
                        token ->
                                token.subject(sellerId.toString())
                                        .claim("roles", List.of("BUYER", "SELLER")))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_BUYER"),
                        new SimpleGrantedAuthority("ROLE_SELLER"));
    }

    private JsonNode example(String type) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/events/" + type + ".json")) {
            return json.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
