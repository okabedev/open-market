package com.openmarket.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.test.container.RedisContainerConfiguration;
import com.openmarket.common.test.openapi.OpenApiSnapshot;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 가입·이메일 인증 API와 outbox 기록(ACC-1). gateway가 /api/auth를 떼고 넘긴 경로(/v1/...)로 부른다. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, RedisContainerConfiguration.class})
class SignupIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Test
    @DisplayName("가입하면 201과 계정 ID를 돌려주고, 같은 트랜잭션에서 이벤트 두 개를 outbox에 쌓는다. 페이로드 필드는 JSON 예시와 같다")
    void registers() throws Exception {
        String email = unique();
        String body = register(email, "password123", "홍길동");
        String accountId = json.readTree(body).get("accountId").asString();

        List<Event> events = events(accountId);
        assertThat(events)
                .extracting(Event::type)
                .containsExactly("AccountRegistered", "EmailVerificationRequested");
        for (Event event : events) {
            assertThat(fieldNames(event.payload())).isEqualTo(fieldNames(example(event.type())));
        }
        assertThat(events.getFirst().payload().get("name").asString()).isEqualTo("홍길동");
        assertThat(events.get(1).payload().get("verificationUrl").asString())
                .startsWith("http://localhost:3000/verify-email?token=");
        assertThat(
                        jdbc.sql(
                                        "SELECT email_verified_at IS NULL FROM account WHERE id = :id::uuid")
                                .param("id", accountId)
                                .query(Boolean.class)
                                .single())
                .isTrue();
    }

    @Test
    @DisplayName("같은 이메일로 다시 가입하면 409 AUTH_EMAIL_TAKEN이다")
    void rejectsDuplicate() throws Exception {
        String email = unique();
        register(email, "password123", "홍길동");

        mockMvc.perform(
                        post("/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request(email.toUpperCase(), "password456", "김철수")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_EMAIL_TAKEN"));
    }

    @Test
    @DisplayName("비밀번호 규칙에 맞지 않으면 400 AUTH_INVALID_PASSWORD, 이름이 비면 400이다")
    void rejectsInvalidInput() throws Exception {
        mockMvc.perform(
                        post("/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request(unique(), "short1", "홍길동")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_PASSWORD"));
        mockMvc.perform(
                        post("/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request(unique(), "password123", " ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("메일의 토큰으로 인증하면 204이고 AccountEmailVerified를 쌓는다. 같은 토큰을 다시 보내도 204이고 이벤트는 늘지 않는다")
    void verifiesEmail() throws Exception {
        String accountId =
                json.readTree(register(unique(), "password123", "홍길동")).get("accountId").asString();
        String token = token(accountId);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(
                            post("/v1/accounts/email-verification")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"token\":\"" + token + "\"}"))
                    .andExpect(status().isNoContent());
        }

        assertThat(events(accountId))
                .extracting(Event::type)
                .containsExactly(
                        "AccountRegistered", "EmailVerificationRequested", "AccountEmailVerified");
        assertThat(fieldNames(events(accountId).get(2).payload()))
                .isEqualTo(fieldNames(example("AccountEmailVerified")));
    }

    @Test
    @DisplayName("없는 토큰은 422 AUTH_VERIFICATION_INVALID다")
    void rejectsUnknownToken() throws Exception {
        mockMvc.perform(
                        post("/v1/accounts/email-verification")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"nope\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AUTH_VERIFICATION_INVALID"));
    }

    @Test
    @DisplayName("재발송은 가입 여부와 상관없이 202다. 인증 전 계정에만 새 메일 이벤트를 쌓는다")
    void resends() throws Exception {
        String email = unique();
        String accountId =
                json.readTree(register(email, "password123", "홍길동")).get("accountId").asString();

        for (String target : List.of(email, unique())) {
            mockMvc.perform(
                            post("/v1/accounts/email-verification/resend")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"email\":\"" + target + "\"}"))
                    .andExpect(status().isAccepted());
        }

        assertThat(events(accountId))
                .extracting(Event::type)
                .containsExactly(
                        "AccountRegistered",
                        "EmailVerificationRequested",
                        "EmailVerificationRequested");
    }

    @Test
    @DisplayName("가입 API 밖의 /v1 경로는 토큰이 있어도 막는다")
    void deniesOtherApiPaths() throws Exception {
        mockMvc.perform(post("/v1/accounts/someone/roles")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("OpenAPI 문서가 프론트 api-client에 저장된 문서와 같다")
    void openApiMatchesClientSnapshot() throws Exception {
        String apiDocs =
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        OpenApiSnapshot.assertMatches("auth", apiDocs);
    }

    // --- 도우미 ---

    record Event(String type, JsonNode payload) {}

    private String register(String email, String password, String name) throws Exception {
        return mockMvc.perform(
                        post("/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request(email, password, name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String request(String email, String password, String name) {
        return json.writeValueAsString(Map.of("email", email, "password", password, "name", name));
    }

    private List<Event> events(String accountId) {
        return jdbc.sql(
                        "SELECT type, payload::text AS payload FROM outbox_event WHERE aggregate_type ="
                                + " 'auth.account' AND aggregate_id = :id ORDER BY id")
                .param("id", accountId)
                .query(
                        (rs, row) ->
                                new Event(
                                        rs.getString("type"),
                                        json.readTree(rs.getString("payload"))))
                .list();
    }

    private String token(String accountId) {
        String url =
                events(accountId).stream()
                        .filter(e -> e.type().equals("EmailVerificationRequested"))
                        .findFirst()
                        .orElseThrow()
                        .payload()
                        .get("verificationUrl")
                        .asString();
        return url.substring(url.indexOf("token=") + "token=".length());
    }

    private JsonNode example(String type) throws IOException {
        return json.readTree(new ClassPathResource("events/" + type + ".json").getInputStream());
    }

    private static Set<String> fieldNames(JsonNode node) {
        return node.propertyNames().stream().collect(Collectors.toSet());
    }

    private static String unique() {
        return "buyer-" + UUID.randomUUID() + "@example.com";
    }
}
