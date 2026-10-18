package com.openmarket.gateway;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.http.HttpMethod;

/**
 * gateway 정책 설정. 값은 infra/config-repo/gateway.yml에 둔다.
 *
 * @param publicPaths 토큰 없이 통과시킬 경로. 판매자·관리자 경로와 내부 경로는 여기에 넣어도 공개되지 않는다
 * @param rateLimit 기본 레이트 리밋(사용자 또는 IP마다 토큰 버킷)
 * @param strictRateLimit 가입처럼 남용을 막아야 하는 라우트에 따로 거는 엄격한 레이트 리밋
 */
@ConfigurationProperties("openmarket.gateway")
public record GatewayProperties(
        @DefaultValue List<PublicPath> publicPaths,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue StrictRateLimit strictRateLimit) {

    /**
     * 토큰 없이 통과시킬 경로 하나.
     *
     * @param method HTTP 메서드
     * @param pattern gateway 기준 경로 패턴. 예: {@code /api/catalog/v1/products/**}
     */
    public record PublicPath(HttpMethod method, String pattern) {}

    /**
     * 토큰 버킷 크기와 채우는 속도.
     *
     * @param replenishRate 초당 채우는 토큰 수(지속 허용량)
     * @param burstCapacity 버킷 크기(순간 허용량)
     */
    public record RateLimit(
            @DefaultValue("20") int replenishRate, @DefaultValue("40") int burstCapacity) {}

    /**
     * 엄격한 한도. 라우트마다 버킷을 따로 둔다.
     *
     * @param requestsPerMinute 사용자 또는 IP마다 1분에 허용하는 요청 수. 한 번에 다 써도 된다
     */
    public record StrictRateLimit(@DefaultValue("5") int requestsPerMinute) {}
}
