package com.openmarket.gateway;

import java.net.InetSocketAddress;
import org.jspecify.annotations.Nullable;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * 기본 레이트 리밋(NFR-5). config-repo의 {@code default-filters}가 {@code RequestRateLimiter}로 두 빈을 참조한다.
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
    KeyResolver clientKeyResolver() {
        RemoteAddressResolver addresses = XForwardedRemoteAddressResolver.maxTrustedIndex(1);
        return exchange ->
                exchange.getPrincipal()
                        .map(principal -> "user:" + principal.getName())
                        .switchIfEmpty(
                                Mono.fromSupplier(() -> "ip:" + host(addresses.resolve(exchange))));
    }

    @Bean
    RedisRateLimiter defaultRateLimiter(GatewayProperties properties) {
        return new RedisRateLimiter(
                properties.rateLimit().replenishRate(), properties.rateLimit().burstCapacity());
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
