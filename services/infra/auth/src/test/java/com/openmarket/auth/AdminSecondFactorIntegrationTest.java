package com.openmarket.auth;

import static com.openmarket.auth.SecondFactorLogins.code;
import static com.openmarket.auth.SecondFactorLogins.confirmEnrollment;
import static com.openmarket.auth.SecondFactorLogins.enrolledAdmin;
import static com.openmarket.auth.SecondFactorLogins.enrollmentSecret;
import static com.openmarket.auth.SecondFactorLogins.insertAccount;
import static com.openmarket.auth.SecondFactorLogins.newIp;
import static com.openmarket.auth.SecondFactorLogins.passwordLogin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.auth.SecondFactorLogins.EnrolledAdmin;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.test.container.RedisContainerConfiguration;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * 관리자 2단계 인증(R15, docs/architecture/auth.md "관리자 2단계 인증"). 비밀번호와 TOTP 사이에 인가 코드가 나가지 않는지, 등록 전 첫
 * 로그인, 같은 코드 재사용, 틀린 코드 잠금, 복구 코드, 런북의 등록 해제 SQL을 본다.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@ExtendWith(OutputCaptureExtension.class)
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, RedisContainerConfiguration.class})
class AdminSecondFactorIntegrationTest {

    static final String VERIFIER = "verifier-0123456789-0123456789-0123456789-0123456789";

    @Autowired MockMvc mockMvc;

    @Autowired JdbcClient jdbc;

    @Autowired PasswordEncoder passwordEncoder;

    @Autowired PostgreSQLContainer postgres;

    @Test
    @DisplayName("비밀번호만 맞힌 관리자 세션은 인가 코드를 받지 못하고 2단계 화면으로 간다. 어느 클라이언트든 같다")
    void passwordOnlyAdminSessionGetsNoCode() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@openmarket.local";
        insertAccount(jdbc, passwordEncoder, email, "admin-pass-1234", "ADMIN");
        MockHttpSession session = passwordLogin(mockMvc, email, "admin-pass-1234");

        for (String client : List.of("admin", "shop")) {
            String redirect =
                    mockMvc.perform(authorizeRequest(session, client))
                            .andExpect(status().is3xxRedirection())
                            .andReturn()
                            .getResponse()
                            .getRedirectedUrl();
            assertSecondFactorPage(redirect);
        }
    }

    @Test
    @DisplayName("등록하지 않은 관리자는 첫 로그인에 등록하고, 등록을 마치면 원래 인가 요청으로 돌아가 코드를 받는다")
    void firstLoginEnrollsThenContinues() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@openmarket.local";
        insertAccount(jdbc, passwordEncoder, email, "admin-pass-1234", "ADMIN");
        MockHttpSession session = passwordLogin(mockMvc, email, "admin-pass-1234");
        mockMvc.perform(authorizeRequest(session, "admin"))
                .andExpect(r -> assertSecondFactorPage(r.getResponse().getRedirectedUrl()));

        byte[] secret = enrollmentSecret(mockMvc, session);
        // 등록 전에 화면을 다시 열어도 같은 비밀 값이다.
        assertThat(enrollmentSecret(mockMvc, session)).isEqualTo(secret);
        List<String> recoveryCodes = confirmEnrollment(mockMvc, session, code(secret, 0));

        assertThat(recoveryCodes).hasSize(10);
        assertThat(authorizeCode(session, "admin")).isNotBlank();
    }

    @Test
    @DisplayName("등록한 관리자는 코드를 맞혀야 저장한 인가 요청으로 돌아가고, 같은 코드는 다시 받지 않는다")
    void verifiesCodeAndRejectsReplay() throws Exception {
        EnrolledAdmin admin = enrolledAdmin(mockMvc, jdbc, passwordEncoder);
        MockHttpSession session = passwordLogin(mockMvc, admin.email(), "admin-pass-1234");
        mockMvc.perform(authorizeRequest(session, "admin"))
                .andExpect(r -> assertSecondFactorPage(r.getResponse().getRedirectedUrl()));

        mockMvc.perform(totp(session, "000000")).andExpect(redirectedUrl("/login/totp?error"));
        String code = code(admin.secret(), 1);
        String back =
                mockMvc.perform(totp(session, code))
                        .andExpect(status().is3xxRedirection())
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();
        assertThat(back).contains("/oauth2/authorize").contains("client_id=admin");
        assertThat(authorizeCode(session, "admin")).isNotBlank();

        MockHttpSession again = passwordLogin(mockMvc, admin.email(), "admin-pass-1234");
        mockMvc.perform(totp(again, code)).andExpect(redirectedUrl("/login/totp?error"));
    }

    @Test
    @DisplayName("틀린 코드 5회째에 계정을 15분 잠그고 세션을 끝낸다. 비밀번호가 맞아도 횟수는 지우지 않는다. 실패와 잠금을 보안 이벤트로 남긴다")
    void locksAfterFiveWrongCodes(CapturedOutput output) throws Exception {
        EnrolledAdmin admin = enrolledAdmin(mockMvc, jdbc, passwordEncoder);
        MockHttpSession session = passwordLogin(mockMvc, admin.email(), "admin-pass-1234");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(totp(session, "000000")).andExpect(redirectedUrl("/login/totp?error"));
        }
        // 다시 로그인해도 횟수가 이어진다(비밀번호 성공으로 지우면 코드를 끝없이 추측할 수 있다).
        session = passwordLogin(mockMvc, admin.email(), "admin-pass-1234");
        mockMvc.perform(totp(session, "111111")).andExpect(redirectedUrl("/login/totp?error"));
        mockMvc.perform(totp(session, "222222")).andExpect(redirectedUrl("/login?locked"));

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(
                        post("/login")
                                .with(csrf())
                                .with(newIp())
                                .param("username", admin.email())
                                .param("password", "admin-pass-1234"))
                .andExpect(redirectedUrl("/login?locked"));
        assertThat(output)
                .contains("보안 이벤트 SECOND_FACTOR_FAILED account=" + admin.id())
                .contains("보안 이벤트 ACCOUNT_LOCKED account=" + admin.id())
                .doesNotContain(admin.email());
    }

    @Test
    @DisplayName("복구 코드는 코드 대신 한 번 쓸 수 있다")
    void recoveryCodeWorksOnce() throws Exception {
        EnrolledAdmin admin = enrolledAdmin(mockMvc, jdbc, passwordEncoder);
        String recovery = admin.recoveryCodes().getFirst();

        MockHttpSession session = passwordLogin(mockMvc, admin.email(), "admin-pass-1234");
        mockMvc.perform(totp(session, recovery)).andExpect(status().is3xxRedirection());
        assertThat(authorizeCode(session, "admin")).isNotBlank();

        MockHttpSession again = passwordLogin(mockMvc, admin.email(), "admin-pass-1234");
        mockMvc.perform(totp(again, recovery)).andExpect(redirectedUrl("/login/totp?error"));
    }

    @Test
    @DisplayName("관리자가 아닌 계정은 2단계 없이 코드를 받고, 2단계 화면은 열 수 없다")
    void nonAdminIsUnaffected() throws Exception {
        String email = "buyer-" + UUID.randomUUID() + "@example.com";
        insertAccount(jdbc, passwordEncoder, email, "buyer-pass-1234", "BUYER");
        MockHttpSession session = passwordLogin(mockMvc, email, "buyer-pass-1234");

        assertThat(authorizeCode(session, "shop")).isNotBlank();
        mockMvc.perform(get("/login/totp").session(session)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2단계 확인도 로그인과 같은 IP 한도를 받는다. 넘으면 확인하지 않고 2단계 화면의 안내로 보낸다")
    void secondFactorSharesLoginRateLimit() throws Exception {
        EnrolledAdmin admin = enrolledAdmin(mockMvc, jdbc, passwordEncoder);
        MockHttpSession session = passwordLogin(mockMvc, admin.email(), "admin-pass-1234");
        RequestPostProcessor ip = newIp();
        awayFromWindowEdge();
        for (int i = 0; i < 8; i++) {
            mockMvc.perform(
                            post("/login")
                                    .with(csrf())
                                    .with(ip)
                                    .param("username", "nobody-" + i + "@example.com")
                                    .param("password", "wrong-password-1"))
                    .andExpect(redirectedUrl("/login?error"));
        }

        mockMvc.perform(
                        post("/login/totp")
                                .session(session)
                                .with(csrf())
                                .with(ip)
                                .param("code", code(admin.secret(), 1)))
                .andExpect(redirectedUrl("/login/totp?limited"));
    }

    @Test
    @DisplayName("런북의 등록 해제 SQL은 등록과 복구 코드, 인가 기록을 지우고 감사 로그를 남긴다. 다음 로그인에 다시 등록한다")
    void runbookResetsEnrollment() throws Exception {
        EnrolledAdmin admin = enrolledAdmin(mockMvc, jdbc, passwordEncoder);
        assertThat(authorizeCode(admin.session(), "admin")).isNotBlank();
        UUID operator = UUID.randomUUID();

        ExecResult result = resetTotp(admin.email().toUpperCase(), operator.toString());

        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        assertThat(count("SELECT count(*) FROM totp_credential WHERE account_id = ?", admin.id()))
                .isZero();
        assertThat(
                        count(
                                "SELECT count(*) FROM totp_recovery_code WHERE account_id = ?",
                                admin.id()))
                .isZero();
        assertThat(
                        count(
                                "SELECT count(*) FROM oauth2_authorization WHERE principal_name = ?",
                                admin.id().toString()))
                .isZero();
        assertThat(
                        jdbc.sql(
                                        "SELECT actor_id, target_type, detail FROM audit_log"
                                                + " WHERE action = 'TOTP_ENROLLMENT_RESET'"
                                                + " AND target_id = ?")
                                .param(admin.id().toString())
                                .query(
                                        (rs, n) ->
                                                List.of(
                                                        rs.getString(1),
                                                        rs.getString(2),
                                                        rs.getString(3)))
                                .list())
                .containsExactly(List.of(operator.toString(), "ACCOUNT", "기기 분실"));

        MockHttpSession session = passwordLogin(mockMvc, admin.email(), "admin-pass-1234");
        mockMvc.perform(authorizeRequest(session, "admin"))
                .andExpect(r -> assertSecondFactorPage(r.getResponse().getRedirectedUrl()));
        assertThat(enrollmentSecret(mockMvc, session)).isNotEqualTo(admin.secret());
    }

    @Test
    @DisplayName("등록 해제 SQL을 다시 실행해도 감사 로그는 한 번만 남고, 없는 계정이면 아무것도 바꾸지 않고 실패한다")
    void runbookResetIsIdempotent() throws Exception {
        EnrolledAdmin admin = enrolledAdmin(mockMvc, jdbc, passwordEncoder);
        String operator = UUID.randomUUID().toString();

        assertThat(resetTotp(admin.email(), operator).getExitCode()).isZero();
        assertThat(resetTotp(admin.email(), operator).getExitCode()).isZero();
        ExecResult missing =
                resetTotp("nobody-" + UUID.randomUUID() + "@openmarket.local", operator);

        assertThat(missing.getExitCode()).isNotZero();
        assertThat(
                        count(
                                "SELECT count(*) FROM audit_log WHERE action = 'TOTP_ENROLLMENT_RESET'"
                                        + " AND actor_id = ?",
                                operator))
                .isEqualTo(1);
    }

    /** 런북 명령과 같게 psql로 {@code infra/runbook/totp-reset.sql}을 실행한다. 테스트 DB는 스키마 auth가 있는 test다. */
    private ExecResult resetTotp(String email, String actor) throws Exception {
        postgres.copyFileToContainer(
                MountableFile.forHostPath(repositoryRoot().resolve("infra/runbook/totp-reset.sql")),
                "/tmp/totp-reset.sql");
        return postgres.execInContainer(
                "psql",
                "-U",
                postgres.getUsername(),
                "-d",
                postgres.getDatabaseName(),
                "-v",
                "ON_ERROR_STOP=1",
                "-v",
                "email=" + email,
                "-v",
                "actor=" + actor,
                "-v",
                "reason=기기 분실",
                "-f",
                "/tmp/totp-reset.sql");
    }

    private long count(String sql, Object id) {
        return jdbc.sql(sql).param(id).query(Long.class).single();
    }

    /** 테스트 작업 디렉터리(서비스 모듈)에서 위로 올라가 레포 루트를 찾는다(OpenApiSnapshot과 같다). */
    private static Path repositoryRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("services"))
                    && Files.isDirectory(dir.resolve("infra"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("레포 루트를 찾지 못했다");
    }

    /** 2단계 화면 주소. 엔트리 포인트가 절대 주소와 요소 안내 조건을 붙인다. */
    private static void assertSecondFactorPage(String url) {
        assertThat(URI.create(url).getPath()).isEqualTo("/login/totp");
    }

    /** 한도는 분 단위 고정 창이다. 창 끝 15초 안이면 다음 창까지 기다린다(LoginPageIntegrationTest와 같다). */
    private static void awayFromWindowEdge() throws InterruptedException {
        long intoWindow = System.currentTimeMillis() % 60_000;
        if (intoWindow > 45_000) {
            Thread.sleep(60_000 - intoWindow + 100);
        }
    }

    private RequestBuilder totp(MockHttpSession session, String code) {
        return post("/login/totp").session(session).with(csrf()).with(newIp()).param("code", code);
    }

    private String authorizeCode(MockHttpSession session, String client) throws Exception {
        String redirect =
                mockMvc.perform(authorizeRequest(session, client))
                        .andExpect(status().is3xxRedirection())
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();
        assertThat(redirect).contains("/auth/callback").contains("code=");
        return redirect;
    }

    private RequestBuilder authorizeRequest(MockHttpSession session, String client)
            throws Exception {
        int port = "admin".equals(client) ? 3002 : 3000;
        return get("/oauth2/authorize")
                .session(session)
                .queryParam("response_type", "code")
                .queryParam("client_id", client)
                .queryParam("scope", "openid profile api")
                .queryParam("redirect_uri", "http://localhost:" + port + "/auth/callback")
                .queryParam("state", "state-1")
                .queryParam("code_challenge", challenge())
                .queryParam("code_challenge_method", "S256");
    }

    private static String challenge() throws Exception {
        byte[] digest =
                MessageDigest.getInstance("SHA-256")
                        .digest(VERIFIER.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
