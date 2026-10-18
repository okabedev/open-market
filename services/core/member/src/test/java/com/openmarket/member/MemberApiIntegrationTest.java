package com.openmarket.member;

import static com.openmarket.member.MemberTokens.buyer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.member.command.application.RegisterMemberCommand;
import com.openmarket.member.command.application.RegisterMemberUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 본인 프로필·배송지 API(ACC-3). 회원은 가입 이벤트 대신 유스케이스로 만든다. 리스너는 {@link AccountEventsIntegrationTest}. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(PostgresContainerConfiguration.class)
class MemberApiIntegrationTest {

    static final String ME = "/v1/members/me";
    static final String ADDRESSES = ME + "/addresses";

    @Autowired MockMvc mockMvc;

    @Autowired RegisterMemberUseCase registerMember;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    UUID memberId;

    @BeforeEach
    void register() {
        memberId = UUID.randomUUID();
        registerMember.execute(new RegisterMemberCommand(memberId, "buyer@example.com", "홍길동"));
    }

    @Test
    @DisplayName("토큰이 없으면 401, 서비스 간 토큰이면 403이다")
    void requiresUserToken() throws Exception {
        mockMvc.perform(get(ME)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ME).with(MemberTokens.internal())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("가입 이벤트가 아직 오지 않은 회원은 404 MEMBER_NOT_FOUND다")
    void memberNotYetCreated() throws Exception {
        mockMvc.perform(get(ME).with(buyer(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("본인 프로필은 원문으로 보이고, 이름과 연락처를 바꿀 수 있다. 개인정보는 로그에 남지 않는다")
    void viewsAndChangesProfile(CapturedOutput output) throws Exception {
        mockMvc.perform(get(ME).with(buyer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(memberId.toString()))
                .andExpect(jsonPath("$.email").value("buyer@example.com"))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.emailVerified").value(false));

        mockMvc.perform(
                        withBody(
                                put(ME).with(buyer(memberId)),
                                Map.of("name", "김철수", "phone", "010-9876-5432")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ME).with(buyer(memberId)))
                .andExpect(jsonPath("$.name").value("김철수"))
                .andExpect(jsonPath("$.phone").value("010-9876-5432"));
        assertThat(output.getAll()).doesNotContain("김철수").doesNotContain("010-9876-5432");
    }

    @Test
    @DisplayName("형식이 틀린 연락처는 400 MEMBER_INVALID_INPUT이고 메시지에 입력값을 넣지 않는다")
    void rejectsInvalidPhone() throws Exception {
        mockMvc.perform(
                        withBody(
                                put(ME).with(buyer(memberId)),
                                Map.of("name", "홍길동", "phone", "01098765432")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_INVALID_INPUT"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        "01098765432"))));
    }

    @Test
    @DisplayName("배송지 추가는 Idempotency-Key가 필요하고, 같은 키로 다시 보내면 같은 배송지를 돌려준다")
    void addAddressIsIdempotent() throws Exception {
        mockMvc.perform(withBody(post(ADDRESSES).with(buyer(memberId)), address("집", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_IDEMPOTENCY_KEY_REQUIRED"));

        String first = addAddress("key-1", address("집", false));
        String again = addAddress("key-1", address("집", false));

        assertThat(again).isEqualTo(first);
        assertThat(addressCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("기본 배송지가 먼저 오고, 기본 지정·수정·삭제가 반영된다. 기본 배송지를 지우면 가장 최근 배송지가 기본이 된다")
    void managesAddresses() throws Exception {
        String home = addAddress("home", address("집", false));
        String office = addAddress("office", address("회사", false));
        String parents = addAddress("parents", address("본가", true));

        JsonNode listed = addresses();
        assertThat(field(listed, "addressId")).containsExactly(parents, office, home);
        assertThat(field(listed, "isDefault")).containsExactly("true", "false", "false");
        assertThat(listed.get(0).get("recipientPhone").asString()).isEqualTo("010-1234-5678");
        assertThat(listed.get(0).get("baseAddress").asString()).isEqualTo("서울특별시 강남구 테헤란로 123");

        mockMvc.perform(post(ADDRESSES + "/" + home + "/default").with(buyer(memberId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(
                        withBody(
                                put(ADDRESSES + "/" + office).with(buyer(memberId)),
                                address("본사", false)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(ADDRESSES + "/" + home).with(buyer(memberId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(ADDRESSES + "/" + home).with(buyer(memberId)))
                .andExpect(status().isNoContent());

        JsonNode after = addresses();
        assertThat(field(after, "addressId")).containsExactly(parents, office);
        assertThat(field(after, "isDefault")).containsExactly("true", "false");
        assertThat(after.get(1).get("label").asString()).isEqualTo("본사");
    }

    @Test
    @DisplayName("다른 회원의 배송지는 고치거나 기본으로 지정할 수 없다(404)")
    void cannotTouchOthersAddress() throws Exception {
        String home = addAddress("home", address("집", false));
        UUID other = UUID.randomUUID();
        registerMember.execute(new RegisterMemberCommand(other, "other@example.com", "김철수"));

        mockMvc.perform(post(ADDRESSES + "/" + home + "/default").with(buyer(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_ADDRESS_NOT_FOUND"));
        mockMvc.perform(
                        withBody(
                                put(ADDRESSES + "/" + home).with(buyer(other)),
                                address("남의 집", false)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(ADDRESSES + "/" + home).with(buyer(other)))
                .andExpect(status().isNoContent());
        assertThat(addressCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("배송지를 동시에 여러 개 추가해도 10개를 넘지 않고, 넘는 요청은 422 MEMBER_ADDRESS_LIMIT이다")
    void limitsAddressesUnderConcurrency() throws Exception {
        int requests = 14;
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            String key = "concurrent-" + i;
            Map<String, Object> body = address("배송지 " + i, false);
            calls.add(
                    () ->
                            mockMvc.perform(
                                            withBody(
                                                    post(ADDRESSES)
                                                            .with(buyer(memberId))
                                                            .header("Idempotency-Key", key),
                                                    body))
                                    .andReturn()
                                    .getResponse()
                                    .getStatus());
        }
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
            for (Future<Integer> result : executor.invokeAll(calls)) {
                statuses.add(result.get());
            }
        }

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(10);
        assertThat(statuses).filteredOn(s -> s == 422).hasSize(requests - 10);
        assertThat(addressCount()).isEqualTo(10);
        assertThat(
                        jdbc.sql("SELECT default_address_id IS NOT NULL FROM member WHERE id = :id")
                                .param("id", memberId)
                                .query(Boolean.class)
                                .single())
                .isTrue();
    }

    private String addAddress(String key, Map<String, Object> body) throws Exception {
        String response =
                mockMvc.perform(
                                withBody(
                                        post(ADDRESSES)
                                                .with(buyer(memberId))
                                                .header("Idempotency-Key", key),
                                        body))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(response).get("addressId").asString();
    }

    private JsonNode addresses() throws Exception {
        return json.readTree(
                mockMvc.perform(get(ADDRESSES).with(buyer(memberId)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private static List<String> field(JsonNode array, String name) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.get(name).asString()));
        return values;
    }

    private static Map<String, Object> address(String label, boolean asDefault) {
        return Map.of(
                "label", label,
                "recipientName", "홍길동",
                "recipientPhone", "010-1234-5678",
                "zipCode", "06236",
                "baseAddress", "서울특별시 강남구 테헤란로 123",
                "detailAddress", "4층",
                "asDefault", asDefault);
    }

    private MockHttpServletRequestBuilder withBody(
            MockHttpServletRequestBuilder request, Map<String, Object> body) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    private int addressCount() {
        return jdbc.sql("SELECT count(*) FROM address WHERE member_id = :id")
                .param("id", memberId)
                .query(Integer.class)
                .single();
    }
}
