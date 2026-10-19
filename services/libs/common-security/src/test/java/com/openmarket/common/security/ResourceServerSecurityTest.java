package com.openmarket.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 서비스의 토큰 재검증(docs/architecture/auth.md "인가" 2). 서명·iss·aud·exp 검증은 Boot의 {@code
 * spring.security.oauth2.resourceserver.jwt.*} 설정이 하고, 이 모듈은 경로 규칙과 권한 변환을 더한다. 실제 서비스와 같은
 * 키(config-repo 공통 {@code application.yml})를 테스트 키로 바꿔 넣는다.
 */
@SpringBootTest(
        properties = {
            "openmarket.security.public-paths[0].method=GET",
            "openmarket.security.public-paths[0].pattern=/public/**",
            "openmarket.security.public-paths[1].method=GET",
            "openmarket.security.public-paths[1].pattern=/v1/seller/**",
            "openmarket.security.public-paths[2].method=GET",
            "openmarket.security.public-paths[2].pattern=/internal/**"
        })
@AutoConfigureMockMvc
class ResourceServerSecurityTest {

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri", TestTokens::jwkSetUri);
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> TestTokens.ISSUER);
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.audiences",
                () -> "shop,seller,admin,internal");
    }

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("사용자 토큰이면 /v1 경로를 통과하고 sub를 주체로 쓴다")
    void userTokenPassesV1() throws Exception {
        call("/v1/me", TestTokens.user("account-1", "BUYER"))
                .andExpect(status().isOk())
                .andExpect(content().string("account-1"));
    }

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(get("/v1/me")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("서명, iss, aud, exp 중 하나라도 맞지 않는 토큰은 401이다")
    void rejectsInvalidToken(String reason, String token) throws Exception {
        call("/v1/me", token).andExpect(status().isUnauthorized());
    }

    static Stream<Arguments> invalidTokens() {
        return Stream.of(
                Arguments.of("모르는 키로 서명", TestTokens.signedByUnknownKey()),
                Arguments.of(
                        "다른 issuer",
                        TestTokens.user(claims -> claims.issuer("http://evil.example"))),
                Arguments.of("모르는 audience", TestTokens.user(claims -> claims.audience("other"))),
                Arguments.of(
                        "만료",
                        TestTokens.user(
                                claims ->
                                        claims.issueTime(
                                                        Date.from(Instant.now().minusSeconds(3600)))
                                                .expirationTime(
                                                        Date.from(
                                                                Instant.now()
                                                                        .minusSeconds(1800))))));
    }

    @Test
    @DisplayName("판매자 경로는 SELLER, 관리자 경로는 ADMIN 역할이 있어야 한다")
    void rolePaths() throws Exception {
        call("/v1/seller/me", TestTokens.user("buyer-1", "BUYER"))
                .andExpect(status().isForbidden());
        call("/v1/seller/me", TestTokens.user("seller-1", "BUYER", "SELLER"))
                .andExpect(status().isOk());
        call("/v1/admin/me", TestTokens.user("seller-1", "SELLER"))
                .andExpect(status().isForbidden());
        call("/v1/admin/me", TestTokens.user("admin-1", "ADMIN")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("내부 API는 internal scope의 서비스 간 토큰만 받는다")
    void internalPathNeedsInternalScope() throws Exception {
        call("/internal/v1/me", TestTokens.internal("notification"))
                .andExpect(status().isOk())
                .andExpect(content().string("notification"));
        call("/internal/v1/me", TestTokens.user("admin-1", "ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("서비스 간 토큰으로는 사용자 API(/v1)를 부를 수 없다")
    void internalTokenCannotCallUserApi() throws Exception {
        call("/v1/me", TestTokens.internal("notification")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("규칙에 없는 경로는 토큰이 있어도 막는다")
    void deniesUnknownPaths() throws Exception {
        call("/other/me", TestTokens.user("admin-1", "ADMIN")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("공개 경로는 토큰 없이 통과하고, 토큰이 있으면 주체를 쓴다")
    void publicPathPassesWithoutToken() throws Exception {
        mockMvc.perform(get("/public/me"))
                .andExpect(status().isOk())
                .andExpect(content().string("anonymous"));
        call("/public/me", TestTokens.user("buyer-1", "BUYER"))
                .andExpect(status().isOk())
                .andExpect(content().string("buyer-1"));
    }

    @Test
    @DisplayName("공개 경로는 적은 메서드만 연다")
    void publicPathOnlyForDeclaredMethod() throws Exception {
        mockMvc.perform(post("/public/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("판매자·관리자·내부 경로는 공개 경로에 적어도 열리지 않는다")
    void rolePathsCannotBePublic() throws Exception {
        mockMvc.perform(get("/v1/seller/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/me")).andExpect(status().isUnauthorized());
    }

    private ResultActions call(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token));
    }
}
