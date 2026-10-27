package com.openmarket.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.openmarket.auth.command.application.DeactivateAccountCommand;
import com.openmarket.auth.command.application.DeactivateAccountUseCase;
import com.openmarket.auth.infra.key.JdbcSigningKeyStore;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.test.container.RedisContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Authorization Server 구성 확인: 고정 issuer(R7), DB 서명 키, 클라이언트 등록, 토큰 클레임과 수명, admin 클라이언트의 역할 검사.
 * 테스트는 무작위 주소로 요청하지만 토큰과 discovery 문서의 issuer는 설정 값이어야 한다.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, RedisContainerConfiguration.class})
class AuthorizationServerIntegrationTest {

    static final String ISSUER = "http://auth.openmarket.local:9000";
    static final String VERIFIER = "verifier-0123456789-0123456789-0123456789-0123456789";

    @Autowired MockMvc mockMvc;

    @Autowired JdbcClient jdbc;

    @Autowired PasswordEncoder passwordEncoder;

    @Autowired JdbcSigningKeyStore signingKeyStore;

    @Autowired DeactivateAccountUseCase deactivateAccount;

    /** 컨테이너 안에서 다른 이름으로 auth에 닿는 경우를 흉내 낸다. */
    static RequestPostProcessor fromHost(String host, int port) {
        return request -> {
            request.setServerName(host);
            request.setServerPort(port);
            return request;
        };
    }

    @Test
    @DisplayName("discovery 문서의 issuer는 요청 주소와 상관없이 설정 값이다")
    void issuerIsFixedRegardlessOfHost() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration").with(fromHost("172.18.0.5", 9000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value(ISSUER))
                .andExpect(jsonPath("$.jwks_uri").value(ISSUER + "/oauth2/jwks"))
                .andExpect(jsonPath("$.end_session_endpoint").value(ISSUER + "/connect/logout"));
    }

    @Test
    @DisplayName("서비스 클라이언트는 client_credentials로 aud=internal, scope=internal 토큰을 받는다")
    void issuesInternalTokenForServiceClient() throws Exception {
        String body =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .with(fromHost("localhost", 54321))
                                        .with(httpBasic("notification", "notification-test-secret"))
                                        .param("grant_type", "client_credentials")
                                        .param("scope", "internal"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Jwt jwt = decode(JsonPath.read(body, "$.access_token"));
        assertThat(jwt.getIssuer()).hasToString(ISSUER);
        assertThat(jwt.getSubject()).isEqualTo("notification");
        assertThat(jwt.getAudience()).containsExactly("internal");
        assertThat(jwt.getClaimAsStringList("scope")).containsExactly("internal");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("사용자 클라이언트는 client_credentials를 쓸 수 없다")
    void userClientCannotUseClientCredentials() throws Exception {
        mockMvc.perform(
                        post("/oauth2/token")
                                .with(httpBasic("shop", "shop-test-secret"))
                                .param("grant_type", "client_credentials"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unauthorized_client"));
    }

    @Test
    @DisplayName(
            "관리자 시드 계정이 PKCE로 로그인하면 sub=accountId, roles, aud=클라이언트, 10분 토큰을 받고 갱신하면 리프레시 토큰이 바뀐다")
    void authorizationCodeWithPkceAndRefreshRotation() throws Exception {
        MockHttpSession session = login("admin@openmarket.local", "admin-test-pass-1");

        String tokens = exchange("admin", "admin-test-secret", authorize(session, "admin", 3002));

        Jwt access = decode(JsonPath.read(tokens, "$.access_token"));
        String adminId =
                jdbc.sql("SELECT id::text FROM account WHERE email = 'admin@openmarket.local'")
                        .query(String.class)
                        .single();
        assertThat(access.getSubject()).isEqualTo(adminId);
        assertThat(access.getIssuer()).hasToString(ISSUER);
        assertThat(access.getAudience()).containsExactly("admin");
        assertThat(access.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(access.getClaimAsBoolean("email_verified")).isTrue();
        assertThat(access.getClaimAsStringList("scope"))
                .containsExactlyInAnyOrder("openid", "profile", "api");
        assertThat(Duration.between(access.getIssuedAt(), access.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(10));
        assertThat((String) JsonPath.read(tokens, "$.id_token")).isNotBlank();

        String refreshToken = JsonPath.read(tokens, "$.refresh_token");
        String refreshed =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .with(httpBasic("admin", "admin-test-secret"))
                                        .param("grant_type", "refresh_token")
                                        .param("refresh_token", refreshToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat((String) JsonPath.read(refreshed, "$.refresh_token")).isNotEqualTo(refreshToken);

        // 쓴 리프레시 토큰은 다시 쓸 수 없다(rotation).
        mockMvc.perform(
                        post("/oauth2/token")
                                .with(httpBasic("admin", "admin-test-secret"))
                                .param("grant_type", "refresh_token")
                                .param("refresh_token", refreshToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    @DisplayName("admin 클라이언트는 ADMIN 역할이 없는 계정의 인가 요청을 access_denied로 돌려보낸다")
    void adminClientRejectsNonAdmin() throws Exception {
        String email = "buyer-" + UUID.randomUUID() + "@example.com";
        insertBuyer(email, "buyer-pass-1234");
        MockHttpSession session = login(email, "buyer-pass-1234");

        String redirect =
                mockMvc.perform(authorizeRequest(session, "admin", 3002))
                        .andExpect(status().is3xxRedirection())
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();

        assertThat(redirect).startsWith("http://localhost:3002/auth/callback");
        assertThat(query(redirect, "error")).isEqualTo("access_denied");
        assertThat(query(redirect, "code")).isNull();

        // 같은 계정이 shop으로는 로그인할 수 있다.
        String tokens = exchange("shop", "shop-test-secret", authorize(session, "shop", 3000));
        Jwt access = decode(JsonPath.read(tokens, "$.access_token"));
        assertThat(access.getClaimAsStringList("roles")).containsExactly("BUYER");
        assertThat(access.getClaimAsBoolean("email_verified")).isFalse();
        assertThat(access.getAudience()).containsExactly("shop");
    }

    @Test
    @DisplayName("DLT 메시지 관리자 API는 ADMIN 역할 토큰만 부른다. 구매자 토큰은 403, 토큰이 없으면 401")
    void deadLetterAdminApiRequiresAdmin() throws Exception {
        MockHttpSession adminSession = login("admin@openmarket.local", "admin-test-pass-1");
        String adminToken =
                JsonPath.read(
                        exchange(
                                "admin",
                                "admin-test-secret",
                                authorize(adminSession, "admin", 3002)),
                        "$.access_token");
        String email = "buyer-" + UUID.randomUUID() + "@example.com";
        insertBuyer(email, "buyer-pass-1234");
        MockHttpSession buyerSession = login(email, "buyer-pass-1234");
        String buyerToken =
                JsonPath.read(
                        exchange("shop", "shop-test-secret", authorize(buyerSession, "shop", 3000)),
                        "$.access_token");

        mockMvc.perform(
                        get("/v1/admin/dead-letters")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(
                        get("/v1/admin/dead-letters")
                                .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/admin/dead-letters")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인하지 않은 브라우저가 admin 클라이언트로 인가 요청하면 거절하지 않고 로그인 화면으로 보낸다")
    void adminClientSendsAnonymousUserToLogin() throws Exception {
        String redirect =
                mockMvc.perform(
                                get("/oauth2/authorize")
                                        .accept(MediaType.TEXT_HTML)
                                        .queryParam("response_type", "code")
                                        .queryParam("client_id", "admin")
                                        .queryParam("scope", "openid profile api")
                                        .queryParam(
                                                "redirect_uri",
                                                "http://localhost:3002/auth/callback")
                                        .queryParam("state", "state-1")
                                        .queryParam("code_challenge", challenge(VERIFIER))
                                        .queryParam("code_challenge_method", "S256"))
                        .andExpect(status().is3xxRedirection())
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();

        assertThat(redirect).endsWith("/login");
    }

    @Test
    @DisplayName("역할이 바뀌면 다음 토큰 갱신부터 roles에 반영된다(SEL-2)")
    void refreshReflectsRoleChange() throws Exception {
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        UUID id = insertBuyer(email, "buyer-pass-1234");
        MockHttpSession session = login(email, "buyer-pass-1234");
        String tokens =
                exchange("seller", "seller-test-secret", authorize(session, "seller", 3001));
        assertThat(decode(JsonPath.read(tokens, "$.access_token")).getClaimAsStringList("roles"))
                .containsExactly("BUYER");

        jdbc.sql("INSERT INTO account_role (account_id, role) VALUES (:id, 'SELLER')")
                .param("id", id)
                .update();
        String refreshed =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .with(httpBasic("seller", "seller-test-secret"))
                                        .param("grant_type", "refresh_token")
                                        .param(
                                                "refresh_token",
                                                (String) JsonPath.read(tokens, "$.refresh_token")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(decode(JsonPath.read(refreshed, "$.access_token")).getClaimAsStringList("roles"))
                .containsExactlyInAnyOrder("BUYER", "SELLER");
    }

    @Test
    @DisplayName("탈퇴로 비활성화한 계정은 토큰을 갱신할 수 없고 남은 로그인 세션으로 인가 코드를 받을 수 없다. 로그아웃은 된다(ACC-4)")
    void deactivatedAccountLosesTokensAndSession() throws Exception {
        String email = "leaving-" + UUID.randomUUID() + "@example.com";
        UUID id = insertBuyer(email, "buyer-pass-1234");
        MockHttpSession session = login(email, "buyer-pass-1234");
        String tokens = exchange("shop", "shop-test-secret", authorize(session, "shop", 3000));

        deactivateAccount.execute(new DeactivateAccountCommand(id));

        mockMvc.perform(
                        post("/oauth2/token")
                                .with(httpBasic("shop", "shop-test-secret"))
                                .param("grant_type", "refresh_token")
                                .param(
                                        "refresh_token",
                                        (String) JsonPath.read(tokens, "$.refresh_token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
        String redirect =
                mockMvc.perform(authorizeRequest(session, "shop", 3000))
                        .andExpect(status().is3xxRedirection())
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();
        assertThat(redirect).endsWith("/login");
        mockMvc.perform(
                        get("/connect/logout")
                                .queryParam(
                                        "id_token_hint",
                                        (String) JsonPath.read(tokens, "$.id_token"))
                                .queryParam("post_logout_redirect_uri", "http://localhost:3000/")
                                .queryParam("client_id", "shop"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/"));
    }

    @Test
    @DisplayName("PKCE 없는 인가 요청은 거절한다")
    void requiresPkce() throws Exception {
        MockHttpSession session = login("admin@openmarket.local", "admin-test-pass-1");

        String redirect =
                mockMvc.perform(
                                get("/oauth2/authorize")
                                        .session(session)
                                        .queryParam("response_type", "code")
                                        .queryParam("client_id", "shop")
                                        .queryParam("scope", "openid profile api")
                                        .queryParam(
                                                "redirect_uri",
                                                "http://localhost:3000/auth/callback")
                                        .queryParam("state", "s1"))
                        .andExpect(status().is3xxRedirection())
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();

        assertThat(query(redirect, "error")).isEqualTo("invalid_request");
    }

    @Test
    @DisplayName("서명 키는 DB에 하나 만들어 두고 다시 읽어도 같은 키를 쓴다. JWK 세트에는 공개 키만 나간다")
    void signingKeyIsPersistedOnce() throws Exception {
        String jwks =
                mockMvc.perform(get("/oauth2/jwks"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JWKSet set = JWKSet.parse(jwks);

        List<String> ids = jdbc.sql("SELECT id FROM signing_key").query(String.class).list();
        assertThat(ids).hasSize(1);
        assertThat(set.getKeys()).extracting(k -> k.getKeyID()).containsExactlyElementsOf(ids);
        assertThat(set.getKeys().getFirst().isPrivate()).isFalse();
        assertThat(signingKeyStore.loadOrCreate().getKeyID()).isEqualTo(ids.getFirst());
    }

    @Test
    @DisplayName("클라이언트 시크릿은 해시로 저장하고, 다시 동기화해도 행이 늘지 않는다")
    void clientsAreStoredWithHashedSecrets() {
        List<String> clients =
                jdbc.sql("SELECT client_id FROM oauth2_registered_client ORDER BY client_id")
                        .query(String.class)
                        .list();
        assertThat(clients).containsExactly("admin", "notification", "seller", "shop");

        String secret =
                jdbc.sql(
                                "SELECT client_secret FROM oauth2_registered_client WHERE client_id = 'shop'")
                        .query(String.class)
                        .single();
        assertThat(secret).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches("shop-test-secret", secret)).isTrue();
    }

    // --- 도우미 ---

    private MockHttpSession login(String email, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(
                        post("/login")
                                .session(session)
                                .with(csrf())
                                .param("username", email)
                                .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getRedirectedUrl())
                                        .doesNotContain("error"));
        return session;
    }

    private RequestBuilder authorizeRequest(MockHttpSession session, String clientId, int appPort)
            throws Exception {
        return get("/oauth2/authorize")
                .session(session)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("scope", "openid profile api")
                .queryParam("redirect_uri", "http://localhost:" + appPort + "/auth/callback")
                .queryParam("state", "state-1")
                .queryParam("code_challenge", challenge(VERIFIER))
                .queryParam("code_challenge_method", "S256");
    }

    private String authorize(MockHttpSession session, String clientId, int appPort)
            throws Exception {
        String redirect =
                mockMvc.perform(authorizeRequest(session, clientId, appPort))
                        .andExpect(status().is3xxRedirection())
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();
        assertThat(redirect).startsWith("http://localhost:" + appPort + "/auth/callback");
        assertThat(query(redirect, "state")).isEqualTo("state-1");
        String code = query(redirect, "code");
        assertThat(code).isNotNull();
        return code;
    }

    private String exchange(String clientId, String secret, String code) throws Exception {
        int appPort =
                switch (clientId) {
                    case "shop" -> 3000;
                    case "seller" -> 3001;
                    default -> 3002;
                };
        return mockMvc.perform(
                        post("/oauth2/token")
                                .with(httpBasic(clientId, secret))
                                .param("grant_type", "authorization_code")
                                .param("code", code)
                                .param(
                                        "redirect_uri",
                                        "http://localhost:" + appPort + "/auth/callback")
                                .param("code_verifier", VERIFIER))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Jwt decode(String token) throws Exception {
        String jwks =
                mockMvc.perform(get("/oauth2/jwks")).andReturn().getResponse().getContentAsString();
        RSAKey key = (RSAKey) JWKSet.parse(jwks).getKeys().getFirst();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder.decode(token);
    }

    private UUID insertBuyer(String email, String password) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO account (id, email, password_hash, status, created_at, updated_at,"
                                + " version) VALUES (:id, :email, :hash, 'ACTIVE', now(), now(), 0)")
                .param("id", id)
                .param("email", email)
                .param("hash", passwordEncoder.encode(password))
                .update();
        jdbc.sql("INSERT INTO account_role (account_id, role) VALUES (:id, 'BUYER')")
                .param("id", id)
                .update();
        return id;
    }

    private static String challenge(String verifier) throws Exception {
        byte[] digest =
                MessageDigest.getInstance("SHA-256")
                        .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static @Nullable String query(String url, String name) {
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name);
    }
}
