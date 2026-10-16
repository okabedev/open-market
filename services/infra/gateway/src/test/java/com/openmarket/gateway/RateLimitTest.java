package com.openmarket.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * 한도를 낮춰 Redis 토큰 버킷이 키(사용자 또는 IP)마다 따로 동작하는지 본다.
 *
 * <p>Redis 스크립트는 초 단위로 토큰을 채우므로 몇 번째 요청에서 막히는지는 고정하지 않고, 한도 안에서 막히는지만 본다.
 */
@TestPropertySource(
        properties = {
            "openmarket.gateway.rate-limit.replenish-rate=1",
            "openmarket.gateway.rate-limit.burst-capacity=2"
        })
class RateLimitTest extends GatewayTestSupport {

    private static final String PROTECTED = "/api/member/v1/members/me";
    private static final String PUBLIC = "/api/member/v1/ping";

    @Test
    @DisplayName("한도를 넘으면 429와 남은 토큰 헤더를 돌려주고 서비스까지 가지 않는다")
    void rejectsOverLimit() throws Exception {
        Map<String, String> headers = bearer(TestJwt.token("limited-user", "BUYER"));

        HttpResponse<String> limited = sendUntilLimited(PROTECTED, headers);

        assertThat(limited.headers().firstValue("X-RateLimit-Remaining")).contains("0");
    }

    @Test
    @DisplayName("로그인한 사용자는 사용자마다 따로 센다")
    void limitsPerUser() throws Exception {
        sendUntilLimited(PROTECTED, bearer(TestJwt.token("user-a", "BUYER")));

        assertThat(get(PROTECTED, bearer(TestJwt.token("user-b", "BUYER"))).statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("익명 요청은 BFF가 붙인 X-Forwarded-For의 마지막 주소마다 따로 센다")
    void limitsAnonymousPerForwardedAddress() throws Exception {
        sendUntilLimited(PUBLIC, Map.of("X-Forwarded-For", "198.51.100.7, 203.0.113.1"));

        assertThat(get(PUBLIC, Map.of("X-Forwarded-For", "198.51.100.7, 203.0.113.2")).statusCode())
                .isEqualTo(200);
    }

    /** 429가 나올 때까지 보낸다. 버스트(2) 안에서는 통과하고, 몇 번 안에 막혀야 하며, 막힌 요청은 서비스까지 가지 않아야 한다. */
    private HttpResponse<String> sendUntilLimited(String path, Map<String, String> headers)
            throws Exception {
        assertThat(get(path, headers).statusCode()).isEqualTo(200);
        for (int i = 0; i < 10; i++) {
            int before = upstream.hits();
            HttpResponse<String> response = get(path, headers);
            if (response.statusCode() == 429) {
                assertThat(upstream.hits()).isEqualTo(before);
                return response;
            }
            assertThat(response.statusCode()).isEqualTo(200);
        }
        throw new AssertionError("한도를 넘겨도 429가 나오지 않았다");
    }

    private static Map<String, String> bearer(String token) {
        return Map.of("Authorization", "Bearer " + token);
    }
}
