package com.openmarket.member;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.openmarket.member.MemberTokens.admin;
import static com.openmarket.member.MemberTokens.buyer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.member.command.application.RegisterMemberCommand;
import com.openmarket.member.command.application.RegisterMemberUseCase;
import com.openmarket.member.command.application.VerifyMemberEmailCommand;
import com.openmarket.member.command.application.VerifyMemberEmailUseCase;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * 탈퇴(ACC-4): 가명 처리, 입점 신청 정리, MemberWithdrawn outbox, 판매자·진행 중 주문 탈퇴 거절. order 내부 API와 auth 토큰
 * 엔드포인트는 WireMock({@link OrderStub})이다.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class WithdrawalIntegrationTest {

    static final String ME = "/v1/members/me";

    @Autowired MockMvc mockMvc;

    @Autowired RegisterMemberUseCase registerMember;

    @Autowired VerifyMemberEmailUseCase verifyMemberEmail;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    UUID memberId;

    @DynamicPropertySource
    static void order(DynamicPropertyRegistry registry) {
        OrderStub.register(registry);
    }

    @BeforeEach
    void verifiedMember() {
        OrderStub.reset();
        OrderStub.ongoing(false);
        memberId = UUID.randomUUID();
        registerMember.execute(new RegisterMemberCommand(memberId, "leaving@example.com", "홍길동"));
        verifyMemberEmail.execute(new VerifyMemberEmailCommand(memberId));
    }

    @Test
    @DisplayName("탈퇴하면 개인정보와 배송지가 남지 않고, 그 뒤로는 없는 회원(404)이다. MemberWithdrawn은 한 번만 쌓인다")
    void withdraws() throws Exception {
        mockMvc.perform(
                        post(ME + "/addresses")
                                .with(buyer(memberId))
                                .header("Idempotency-Key", "a1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "recipientName", "홍길동",
                                                        "recipientPhone", "010-1234-5678",
                                                        "zipCode", "06236",
                                                        "baseAddress", "서울특별시 강남구 테헤란로 123"))))
                .andExpect(status().isCreated());
        applyAsSeller();

        mockMvc.perform(post(ME + "/withdraw").with(buyer(memberId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(ME + "/withdraw").with(buyer(memberId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ME).with(buyer(memberId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        Map<String, Object> row =
                jdbc.sql("SELECT name, email, phone, status FROM member WHERE id = :id")
                        .param("id", memberId)
                        .query()
                        .singleRow();
        assertThat(row)
                .containsEntry("name", "탈퇴회원")
                .containsEntry("email", "withdrawn-" + memberId + "@withdrawn.invalid")
                .containsEntry("status", "WITHDRAWN");
        assertThat(row.get("phone")).isNull();
        assertThat(count("SELECT count(*) FROM address WHERE member_id = :id")).isZero();
        Map<String, Object> application =
                jdbc.sql(
                                "SELECT status, representative_name, account_holder,"
                                        + " account_number_encrypted FROM seller_application WHERE"
                                        + " member_id = :id")
                        .param("id", memberId)
                        .query()
                        .singleRow();
        assertThat(application)
                .containsEntry("status", "CANCELLED")
                .containsEntry("representative_name", "탈퇴회원")
                .containsEntry("account_holder", "탈퇴회원")
                .containsEntry("account_number_encrypted", "");

        List<String> events =
                jdbc.sql(
                                "SELECT payload::text FROM outbox_event WHERE aggregate_type ="
                                        + " 'member.member' AND aggregate_id = :id AND type ="
                                        + " 'MemberWithdrawn'")
                        .param("id", memberId.toString())
                        .query(String.class)
                        .list();
        assertThat(events).hasSize(1);
        assertThat(json.readTree(events.getFirst()).propertyNames())
                .isEqualTo(example().propertyNames());
    }

    @Test
    @DisplayName("판매자는 탈퇴할 수 없다(422 MEMBER_SELLER_CANNOT_WITHDRAW)")
    void sellerCannotWithdraw() throws Exception {
        UUID applicationId = applyAsSeller();
        mockMvc.perform(
                        post("/v1/admin/seller-applications/" + applicationId + "/approve")
                                .with(admin(UUID.randomUUID())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(ME + "/withdraw").with(buyer(memberId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MEMBER_SELLER_CANNOT_WITHDRAW"));
        mockMvc.perform(get(ME).with(buyer(memberId))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("진행 중인 주문이 있으면 422 MEMBER_ONGOING_ORDERS다. order는 서비스 간 토큰으로 부른다")
    void ongoingOrdersBlockWithdrawal() throws Exception {
        OrderStub.ongoing(true);

        mockMvc.perform(post(ME + "/withdraw").with(buyer(memberId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MEMBER_ONGOING_ORDERS"));

        mockMvc.perform(get(ME).with(buyer(memberId))).andExpect(status().isOk());
        OrderStub.SERVER.verify(
                getRequestedFor(
                                urlPathEqualTo(
                                        "/internal/v1/buyers/" + memberId + "/ongoing-orders"))
                        .withHeader("Authorization", equalTo("Bearer " + OrderStub.ACCESS_TOKEN)));
    }

    @Test
    @DisplayName("order를 부를 수 없으면 탈퇴하지 않고 503 MEMBER_ORDER_UNAVAILABLE이다")
    void orderUnavailable() throws Exception {
        OrderStub.ongoingFail(500);

        mockMvc.perform(post(ME + "/withdraw").with(buyer(memberId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MEMBER_ORDER_UNAVAILABLE"));

        mockMvc.perform(get(ME).with(buyer(memberId))).andExpect(status().isOk());
    }

    private UUID applyAsSeller() throws Exception {
        String body =
                mockMvc.perform(
                                post(ME + "/seller-applications")
                                        .with(buyer(memberId))
                                        .header("Idempotency-Key", "s1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "storeName", "길동상회",
                                                                "businessNumber", "1234567891",
                                                                "representativeName", "홍길동",
                                                                "bankName", "국민은행",
                                                                "accountHolder", "홍길동",
                                                                "accountNumber",
                                                                        "110-123-456789"))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(json.readTree(body).get("applicationId").asString());
    }

    private int count(String sql) {
        return jdbc.sql(sql).param("id", memberId).query(Integer.class).single();
    }

    private tools.jackson.databind.JsonNode example() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/events/MemberWithdrawn.json")) {
            if (in == null) {
                throw new IllegalStateException("events/MemberWithdrawn.json이 없다");
            }
            return json.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
