package com.openmarket.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.web.server.LocalManagementPort;

@AutoConfigureTracing
class GatewayTest extends GatewayTestSupport {

    @LocalManagementPort int managementPort;

    @Test
    @DisplayName("공개 경로는 토큰 없이 서비스로 넘기고 /api/<service> 접두어를 뗀다")
    void routesPublicPathWithoutToken() throws Exception {
        HttpResponse<String> response = get("/api/member/v1/ping");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(upstream.last().method()).isEqualTo("GET");
        assertThat(upstream.last().path()).isEqualTo("/v1/ping");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "/api/auth/v1/accounts",
                "/api/auth/v1/accounts/email-verification",
                "/api/auth/v1/accounts/email-verification/resend"
            })
    @DisplayName("가입·이메일 인증 API는 토큰 없이 auth로 넘긴다")
    void routesSignupApisToAuthWithoutToken(String path) throws Exception {
        HttpResponse<String> response = post(path);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(upstream.last().method()).isEqualTo("POST");
        assertThat(upstream.last().path()).isEqualTo(path.substring("/api/auth".length()));
    }

    @Test
    @DisplayName("auth의 다른 경로는 토큰이 있어야 한다")
    void otherAuthPathsNeedToken() throws Exception {
        assertThat(post("/api/auth/v1/accounts/someone/roles").statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("바깥에서 들어온 X-Internal-* 헤더는 지우고 나머지 헤더는 넘긴다")
    void removesInternalHeaders() throws Exception {
        get(
                "/api/member/v1/ping",
                Map.of(
                        "X-Internal-Account-Id", "forged",
                        "x-internal-roles", "ADMIN",
                        "X-Custom", "kept"));

        TestUpstream.Recorded received = upstream.last();
        assertThat(received.header("X-Internal-Account-Id")).isNull();
        assertThat(received.header("X-Internal-Roles")).isNull();
        assertThat(received.header("X-Custom")).isEqualTo("kept");
    }

    @Test
    @DisplayName("공개 경로가 아니면 토큰 없이는 401이고 서비스까지 가지 않는다")
    void rejectsProtectedPathWithoutToken() throws Exception {
        int before = upstream.hits();

        HttpResponse<String> response = get("/api/member/v1/members/me");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(upstream.hits()).isEqualTo(before);
    }

    @Test
    @DisplayName("유효한 토큰이면 서비스로 넘기고 Authorization 헤더를 그대로 전달한다")
    void routesWithValidToken() throws Exception {
        String token = TestJwt.token("account-1", "BUYER");

        HttpResponse<String> response = getWithToken("/api/member/v1/members/me", token);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(upstream.last().path()).isEqualTo("/v1/members/me");
        assertThat(upstream.last().header("Authorization")).isEqualTo("Bearer " + token);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("서명, iss, aud, exp 중 하나라도 맞지 않는 토큰은 401이다")
    void rejectsInvalidToken(String reason, String token) throws Exception {
        HttpResponse<String> response = getWithToken("/api/member/v1/members/me", token);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    static Stream<Arguments> invalidTokens() {
        return Stream.of(
                Arguments.of("모르는 키로 서명", TestJwt.signedByUnknownKey()),
                Arguments.of(
                        "다른 issuer", TestJwt.token(claims -> claims.issuer("http://evil.example"))),
                Arguments.of(
                        "서비스 간 토큰(aud=internal)",
                        TestJwt.token(claims -> claims.audience("internal"))),
                Arguments.of(
                        "만료",
                        TestJwt.token(
                                claims ->
                                        claims.issueTime(
                                                        Date.from(Instant.now().minusSeconds(3600)))
                                                .expirationTime(
                                                        Date.from(
                                                                Instant.now()
                                                                        .minusSeconds(1800))))));
    }

    @Test
    @DisplayName("판매자 경로는 SELLER 역할이 있어야 한다")
    void sellerPathRequiresSellerRole() throws Exception {
        String path = "/api/member/v1/seller/profile";

        assertThat(getWithToken(path, TestJwt.token("buyer-1", "BUYER")).statusCode())
                .isEqualTo(403);
        assertThat(getWithToken(path, TestJwt.token("seller-1", "BUYER", "SELLER")).statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("관리자 경로는 ADMIN 역할이 있어야 한다")
    void adminPathRequiresAdminRole() throws Exception {
        String path = "/api/member/v1/admin/sellers";

        assertThat(getWithToken(path, TestJwt.token("seller-1", "SELLER")).statusCode())
                .isEqualTo(403);
        assertThat(getWithToken(path, TestJwt.token("admin-1", "ADMIN")).statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("서비스의 내부 API와 actuator는 관리자 토큰이 있어도 넘기지 않는다")
    void neverRoutesInternalOrActuatorPaths() throws Exception {
        String admin = TestJwt.token("admin-1", "ADMIN");
        int before = upstream.hits();

        assertThat(getWithToken("/api/member/internal/v1/members/1", admin).statusCode())
                .isEqualTo(403);
        assertThat(getWithToken("/api/member/actuator/env", admin).statusCode()).isEqualTo(403);
        assertThat(upstream.hits()).isEqualTo(before);
    }

    @Test
    @DisplayName("응답의 X-Request-Id는 서비스로 넘긴 traceparent의 trace id다")
    void requestIdIsTraceId() throws Exception {
        HttpResponse<String> response = get("/api/member/v1/ping");

        String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
        assertThat(requestId).matches("[0-9a-f]{32}");
        assertThat(upstream.last().header("traceparent")).contains("-" + requestId + "-");
    }

    @Test
    @DisplayName("오류 응답에도 X-Request-Id를 붙인다")
    void requestIdOnError() throws Exception {
        HttpResponse<String> response = get("/api/member/v1/members/me");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
    }

    @Test
    @DisplayName("actuator는 관리 포트에만 있고 외부 포트에는 없다")
    void actuatorOnlyOnManagementPort() throws Exception {
        assertThat(send(managementPort, "/actuator/health", Map.of()).body()).contains("\"UP\"");
        assertThat(send(managementPort, "/actuator/prometheus", Map.of()).statusCode())
                .isEqualTo(200);
        assertThat(get("/actuator/prometheus").statusCode()).isNotEqualTo(200);
        assertThat(get("/actuator/health").statusCode()).isNotEqualTo(200);
    }
}
