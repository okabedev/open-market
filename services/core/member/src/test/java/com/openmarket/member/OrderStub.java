package com.openmarket.member;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * order 내부 API와 auth 토큰 엔드포인트를 흉내 내는 WireMock 서버. JVM에서 하나만 띄워 테스트 클래스들이 같이 쓴다. 테스트마다 {@link
 * #reset()}으로 스텁을 지운다.
 */
final class OrderStub {

    static final WireMockServer SERVER = new WireMockServer(options().dynamicPort());

    static final String ONGOING = "/internal/v1/buyers/[^/]+/ongoing-orders";
    static final String TOKEN = "/oauth2/token";
    static final String ACCESS_TOKEN = "service-token";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    static {
        SERVER.start();
    }

    private OrderStub() {}

    /** Feign 클라이언트와 토큰 요청이 이 서버로 가게 한다. */
    static void register(DynamicPropertyRegistry registry) {
        registry.add("openmarket.member.order-url", SERVER::baseUrl);
        registry.add(
                "spring.security.oauth2.client.provider.auth.token-uri",
                () -> SERVER.baseUrl() + TOKEN);
    }

    /** 스텁과 요청 기록을 지우고 토큰 엔드포인트만 다시 둔다. */
    static void reset() {
        SERVER.resetAll();
        SERVER.stubFor(
                post(urlPathEqualTo(TOKEN))
                        .willReturn(
                                okJson(
                                        JSON.writeValueAsString(
                                                Map.of(
                                                        "access_token",
                                                        ACCESS_TOKEN,
                                                        "token_type",
                                                        "Bearer",
                                                        "expires_in",
                                                        3600,
                                                        "scope",
                                                        "internal")))));
    }

    /** 진행 중 주문 조회가 {@code ongoing}을 답한다. */
    static void ongoing(boolean ongoing) {
        SERVER.stubFor(
                get(urlPathMatching(ONGOING))
                        .willReturn(
                                okJson(
                                        JSON.writeValueAsString(
                                                Map.of("hasOngoingOrders", ongoing)))));
    }

    static void ongoingFail(int status) {
        SERVER.stubFor(get(urlPathMatching(ONGOING)).willReturn(aResponse().withStatus(status)));
    }
}
