package com.openmarket.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * 가입·이메일 인증 API의 엄격한 레이트 리밋(NFR-5). 한도를 1분에 2회로 낮춰 세 번째 요청이 막히는지 본다.
 *
 * <p>토큰 버킷은 1초마다 1분 한도의 1/60씩 채운다. 요청 하나의 값은 1분치를 한도로 나눈 만큼이라 테스트가 도는 몇 초 안에는 다시 열리지 않는다.
 */
@TestPropertySource(properties = "openmarket.gateway.strict-rate-limit.requests-per-minute=2")
class StrictRateLimitTest extends GatewayTestSupport {

    private static final String SIGNUP = "/api/auth/v1/accounts";
    private static final String VERIFY = "/api/auth/v1/accounts/email-verification";
    private static final String RESEND = "/api/auth/v1/accounts/email-verification/resend";

    @Test
    @DisplayName("한 IP의 가입 요청이 1분 한도를 넘으면 429이고 auth까지 가지 않는다")
    void rejectsSignupOverLimit() throws Exception {
        Map<String, String> client = forwardedFor("198.51.100.10");

        assertThat(post(SIGNUP, client).statusCode()).isEqualTo(200);
        assertThat(post(SIGNUP, client).statusCode()).isEqualTo(200);
        int before = upstream.hits();
        HttpResponse<String> limited = post(SIGNUP, client);

        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(upstream.hits()).isEqualTo(before);
        // 한도 헤더는 기본 한도(버킷 40)의 것이다. 엄격한 리미터의 토큰 수(요청 하나에 60)는 내보내지 않는다.
        assertThat(limited.headers().firstValue("X-RateLimit-Burst-Capacity")).contains("40");
    }

    @Test
    @DisplayName("가입, 인증, 인증 메일 재발송은 IP마다 한 한도를 같이 쓴다")
    void sharesLimitAcrossSignupApis() throws Exception {
        Map<String, String> client = forwardedFor("198.51.100.11");

        assertThat(post(SIGNUP, client).statusCode()).isEqualTo(200);
        assertThat(post(VERIFY, client).statusCode()).isEqualTo(200);

        assertThat(post(RESEND, client).statusCode()).isEqualTo(429);
    }

    @Test
    @DisplayName("다른 IP는 따로 센다")
    void limitsPerAddress() throws Exception {
        Map<String, String> first = forwardedFor("198.51.100.12");
        post(SIGNUP, first);
        post(SIGNUP, first);
        assertThat(post(SIGNUP, first).statusCode()).isEqualTo(429);

        assertThat(post(SIGNUP, forwardedFor("198.51.100.13")).statusCode()).isEqualTo(200);
    }

    private static Map<String, String> forwardedFor(String address) {
        return Map.of("X-Forwarded-For", address);
    }
}
