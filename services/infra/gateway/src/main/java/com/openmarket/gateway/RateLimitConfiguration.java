package com.openmarket.gateway;

import java.net.InetSocketAddress;
import org.jspecify.annotations.Nullable;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * 레이트 리밋(NFR-5). config-repo의 {@code default-filters}가 기본 리미터와 키 리졸버를, 가입 같은 라우트가 엄격한 리미터와 키 리졸버를
 * {@code RequestRateLimiter}로 참조한다. 엄격한 한도를 건 라우트에는 기본 한도도 같이 걸린다.
 *
 * <p>Redis에 닿지 못하면 Spring Cloud Gateway의 {@link RedisRateLimiter}는 요청을 막지 않고 통과시킨다. 레이트 리밋 때문에 전체
 * 서비스가 멈추지 않게 하는 기본 동작을 그대로 쓴다.
 */
@Configuration(proxyBeanMethods = false)
class RateLimitConfiguration {

    /**
     * 키: 로그인한 요청은 {@code user:<sub>}, 익명 요청은 {@code ip:<클라이언트 주소>}.
     *
     * <p>브라우저는 BFF만 부르므로 gateway가 보는 원격 주소는 BFF다. 클라이언트 주소는 BFF가 붙인 {@code X-Forwarded-For}의 마지막 값을
     * 쓴다. 이 값을 믿을 수 있는 것은 gateway에 BFF만 닿는다는 전제 때문이다(ADR 0011).
     */
    @Bean
    @Primary
    KeyResolver clientKeyResolver() {
        RemoteAddressResolver addresses = XForwardedRemoteAddressResolver.maxTrustedIndex(1);
        return exchange ->
                exchange.getPrincipal()
                        .map(principal -> "user:" + principal.getName())
                        .switchIfEmpty(
                                Mono.fromSupplier(() -> "ip:" + host(addresses.resolve(exchange))));
    }

    /**
     * 엄격한 한도의 키: {@code strict:} + 기본 키.
     *
     * <p>Redis 키는 라우트 ID와 이 키로 만든다. 같은 라우트에 기본 한도도 걸리므로 접두어가 없으면 두 리미터가 한 버킷을 다른 한도로 고쳐 쓴다.
     */
    @Bean
    KeyResolver strictKeyResolver(KeyResolver clientKeyResolver) {
        return exchange -> clientKeyResolver.resolve(exchange).map(key -> "strict:" + key);
    }

    @Bean
    @Primary
    RedisRateLimiter defaultRateLimiter(GatewayProperties properties) {
        return new RedisRateLimiter(
                properties.rateLimit().replenishRate(), properties.rateLimit().burstCapacity());
    }

    /**
     * 1분에 N회. 토큰 버킷은 초 단위로 채우므로 요청 하나에 토큰 60개를 쓰고, 1초에 N개씩 채우고, 버킷에는 N회분(60N개)을 담는다.
     *
     * <p>{@code X-RateLimit-*} 헤더는 기본 한도의 것만 준다. 이 리미터의 값은 토큰 단위라 요청 수로 읽히지 않는다.
     */
    @Bean
    RedisRateLimiter strictRateLimiter(GatewayProperties properties) {
        int perMinute = properties.strictRateLimit().requestsPerMinute();
        RedisRateLimiter limiter = new RedisRateLimiter(perMinute, 60L * perMinute, 60);
        limiter.setIncludeHeaders(false);
        return limiter;
    }

    private static String host(@Nullable InetSocketAddress address) {
        if (address == null) {
            return "unknown";
        }
        return address.getAddress() != null
                ? address.getAddress().getHostAddress()
                : address.getHostString();
    }
}
