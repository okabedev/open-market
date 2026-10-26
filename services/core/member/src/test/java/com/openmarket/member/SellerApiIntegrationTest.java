package com.openmarket.member;

import static com.openmarket.member.MemberTokens.admin;
import static com.openmarket.member.MemberTokens.buyer;
import static com.openmarket.member.MemberTokens.seller;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 입점 신청 → 관리자 심사 → 판매자 정지·해제(SEL-1~3). 이벤트는 outbox 행까지, 감사 로그는 audit_log 행까지 본다. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(PostgresContainerConfiguration.class)
class SellerApiIntegrationTest {

    static final String APPLY = "/v1/members/me/seller-applications";
    static final String ACCOUNT_NUMBER = "110-123-456789";

    @Autowired MockMvc mockMvc;

    @Autowired RegisterMemberUseCase registerMember;

    @Autowired VerifyMemberEmailUseCase verifyMemberEmail;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    UUID memberId;
    final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void verifiedMember() {
        memberId = UUID.randomUUID();
        registerMember.execute(new RegisterMemberCommand(memberId, "applicant@example.com", "홍길동"));
        verifyMemberEmail.execute(new VerifyMemberEmailCommand(memberId));
    }

    @Test
    @DisplayName("이메일 인증 전에는 422 MEMBER_EMAIL_NOT_VERIFIED다")
    void requiresVerifiedEmail() throws Exception {
        UUID unverified = UUID.randomUUID();
        registerMember.execute(new RegisterMemberCommand(unverified, "new@example.com", "김철수"));

        submit(unverified, "key-1")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MEMBER_EMAIL_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("신청하면 계좌 번호는 암호화해 저장하고 본인에게는 마스킹한 번호만 보인다. 심사 중에 또 신청하면 409다")
    void submitsApplication(CapturedOutput output) throws Exception {
        UUID applicationId = submitted("key-1");

        mockMvc.perform(get(APPLY).with(buyer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicationId").value(applicationId.toString()))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].businessNumber").value("123-45-67891"))
                .andExpect(jsonPath("$[0].representativeName").value("홍길동"))
                .andExpect(jsonPath("$[0].accountNumberMasked").value("********6789"));
        String stored =
                jdbc.sql(
                                "SELECT account_number_encrypted FROM seller_application WHERE id ="
                                        + " :id")
                        .param("id", applicationId)
                        .query(String.class)
                        .single();
        assertThat(stored).doesNotContain("456789");

        submit(memberId, "key-2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_APPLICATION_PENDING"));
        assertThat(output.getAll()).doesNotContain("110-123-456789").doesNotContain("110123456789");
    }

    @Test
    @DisplayName("관리자 심사 목록은 대표자·예금주·이메일을 마스킹한다. 관리자가 아니면 403이다")
    void listsApplicationsMasked() throws Exception {
        UUID applicationId = submitted("key-1");

        mockMvc.perform(get("/v1/admin/seller-applications").with(buyer(memberId)))
                .andExpect(status().isForbidden());
        JsonNode page = json(get("/v1/admin/seller-applications?size=100").with(admin(adminId)));
        JsonNode item = find(page.get("items"), "applicationId", applicationId.toString());
        assertThat(item.get("applicantEmail").asString()).isEqualTo("ap***@example.com");
        assertThat(item.get("representativeName").asString()).isEqualTo("홍*동");
        assertThat(item.get("accountHolder").asString()).isEqualTo("홍*동");
        assertThat(item.get("accountNumberMasked").asString()).isEqualTo("********6789");
        assertThat(item.get("businessNumber").asString()).isEqualTo("123-45-67891");
        assertThat(page.get("totalCount").asLong()).isPositive();
    }

    @Test
    @DisplayName("승인하면 판매자가 되고 SellerApproved와 감사 로그가 한 번만 남는다. 다시 승인해도 204다")
    void approves() throws Exception {
        UUID applicationId = submitted("key-1");

        review(applicationId, "approve", null).andExpect(status().isNoContent());
        review(applicationId, "approve", null).andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/seller/me").with(seller(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeName").value("길동상회"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(events(memberId, "SellerApproved")).hasSize(1);
        assertThat(json.readTree(events(memberId, "SellerApproved").getFirst()).propertyNames())
                .isEqualTo(example("SellerApproved").propertyNames());
        assertThat(audits(applicationId)).containsExactly("SELLER_APPLICATION_APPROVED");
        assertThat(actor(applicationId)).isEqualTo(adminId.toString());

        submit(memberId, "key-2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_ALREADY_SELLER"));
        review(applicationId, "reject", "늦은 반려")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_APPLICATION_ALREADY_REVIEWED"));
    }

    @Test
    @DisplayName("반려에는 사유가 필요하다. 반려하면 SellerRejected가 나가고 다시 신청할 수 있다")
    void rejects() throws Exception {
        UUID applicationId = submitted("key-1");

        review(applicationId, "reject", " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_INVALID_INPUT"));
        review(applicationId, "reject", "사업자 정보가 확인되지 않습니다").andExpect(status().isNoContent());

        mockMvc.perform(get(APPLY).with(buyer(memberId)))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].rejectReason").value("사업자 정보가 확인되지 않습니다"));
        assertThat(json.readTree(events(memberId, "SellerRejected").getFirst()).propertyNames())
                .isEqualTo(example("SellerRejected").propertyNames());
        assertThat(audits(applicationId)).containsExactly("SELLER_APPLICATION_REJECTED");
        submitted("key-2");
    }

    @Test
    @DisplayName("정지·해제는 상태가 바뀔 때만 이벤트와 감사 로그를 남긴다. 관리자 목록에서 상태를 거를 수 있다")
    void suspendsAndReinstates() throws Exception {
        UUID applicationId = submitted("key-1");
        review(applicationId, "approve", null).andExpect(status().isNoContent());

        sellerAction("suspend", "판매 정책 위반").andExpect(status().isNoContent());
        sellerAction("suspend", "판매 정책 위반").andExpect(status().isNoContent());
        JsonNode suspended =
                json(get("/v1/admin/sellers?status=SUSPENDED&size=100").with(admin(adminId)));
        JsonNode item = find(suspended.get("items"), "sellerId", memberId.toString());
        assertThat(item.get("suspendReason").asString()).isEqualTo("판매 정책 위반");
        assertThat(item.get("representativeName").asString()).isEqualTo("홍*동");

        sellerAction("reinstate", null).andExpect(status().isNoContent());

        assertThat(events(memberId, "SellerSuspended")).hasSize(1);
        assertThat(json.readTree(events(memberId, "SellerSuspended").getFirst()).propertyNames())
                .isEqualTo(example("SellerSuspended").propertyNames());
        assertThat(events(memberId, "SellerReinstated")).hasSize(1);
        assertThat(audits(memberId)).containsExactly("SELLER_SUSPENDED", "SELLER_REINSTATED");
    }

    @Test
    @DisplayName("관리자는 판매자 한 명을 정산 계좌와 함께 본다. 대표자·예금주는 마스킹하고, 관리자가 아니면 403, 없으면 404다")
    void showsSellerToAdmin() throws Exception {
        UUID applicationId = submitted("key-1");
        review(applicationId, "approve", null).andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/admin/sellers/" + memberId).with(buyer(memberId)))
                .andExpect(status().isForbidden());
        JsonNode found = json(get("/v1/admin/sellers/" + memberId).with(admin(adminId)));
        assertThat(found.get("storeName").asString()).isEqualTo("길동상회");
        assertThat(found.get("representativeName").asString()).isEqualTo("홍*동");
        assertThat(found.get("accountHolder").asString()).isEqualTo("홍*동");
        assertThat(found.get("accountNumberMasked").asString()).isEqualTo("********6789");
        assertThat(found.get("bankName").asString()).isNotBlank();
        mockMvc.perform(get("/v1/admin/sellers/" + UUID.randomUUID()).with(admin(adminId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_SELLER_NOT_FOUND"));
    }

    private UUID submitted(String key) throws Exception {
        String body =
                submit(memberId, key)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(json.readTree(body).get("applicationId").asString());
    }

    private ResultActions submit(UUID applicant, String key) throws Exception {
        return mockMvc.perform(
                withBody(
                        post(APPLY).with(buyer(applicant)).header("Idempotency-Key", key),
                        Map.of(
                                "storeName", "길동상회",
                                "businessNumber", "1234567891",
                                "representativeName", "홍길동",
                                "bankName", "국민은행",
                                "accountHolder", "홍길동",
                                "accountNumber", ACCOUNT_NUMBER)));
    }

    private ResultActions review(UUID applicationId, String action, String reason)
            throws Exception {
        MockHttpServletRequestBuilder request =
                post("/v1/admin/seller-applications/" + applicationId + "/" + action)
                        .with(admin(adminId));
        return mockMvc.perform(
                reason == null ? request : withBody(request, Map.of("reason", reason)));
    }

    private ResultActions sellerAction(String action, String reason) throws Exception {
        MockHttpServletRequestBuilder request =
                post("/v1/admin/sellers/" + memberId + "/" + action).with(admin(adminId));
        return mockMvc.perform(
                reason == null ? request : withBody(request, Map.of("reason", reason)));
    }

    private MockHttpServletRequestBuilder withBody(
            MockHttpServletRequestBuilder request, Map<String, Object> body) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    private JsonNode json(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(
                mockMvc.perform(request)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private static JsonNode find(JsonNode items, String field, String value) {
        for (JsonNode item : items) {
            if (item.get(field).asString().equals(value)) {
                return item;
            }
        }
        throw new AssertionError(value + "가 목록에 없다");
    }

    private List<String> events(UUID key, String type) {
        return jdbc.sql(
                        "SELECT payload::text FROM outbox_event WHERE aggregate_type = 'member.seller'"
                                + " AND aggregate_id = :key AND type = :type")
                .param("key", key.toString())
                .param("type", type)
                .query(String.class)
                .list();
    }

    private List<String> audits(UUID target) {
        return jdbc.sql(
                        "SELECT action FROM audit_log WHERE target_id = :target ORDER BY occurred_at,"
                                + " id")
                .param("target", target.toString())
                .query(String.class)
                .list();
    }

    private String actor(UUID target) {
        return jdbc.sql("SELECT actor_id FROM audit_log WHERE target_id = :target")
                .param("target", target.toString())
                .query(String.class)
                .single();
    }

    private JsonNode example(String type) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/events/" + type + ".json")) {
            if (in == null) {
                throw new IllegalStateException("events/" + type + ".json이 없다");
            }
            return json.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
