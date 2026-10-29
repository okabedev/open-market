package com.openmarket.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 레이트 리밋(Redis) 다음의 필터 체인은 Lettuce I/O 스레드에서 돌지 않는다. Lettuce 기본값은 응답 신호를 I/O 스레드에서 보내서, JWT 검증과
 * 라우팅까지 그 스레드에서 돌았다. 요청이 몰리면 다른 Redis 응답 해석이 밀려 Redis 서버는 빠른데 명령 타임아웃(200ms)이 쿠폰 버스트마다 약 1,000건
 * 났다(docs/architecture/risks.md "부하 측정").
 */
@DirtiesContext
@Import(RedisPublishThreadTest.ThreadRecorder.class)
class RedisPublishThreadTest extends GatewayTestSupport {

    private static final String PATH = "/api/member/v1/members/me";

    @Autowired List<String> threadsAfterRateLimit;

    @Test
    @DisplayName("레이트 리밋 다음 필터는 Lettuce I/O 스레드가 아닌 곳에서 돈다")
    void continuesOffLettuceIoThread() throws Exception {
        String token = TestJwt.token("account-1", "BUYER");
        for (int i = 0; i < 5; i++) {
            assertThat(getWithToken(PATH, token).statusCode()).isEqualTo(200);
        }

        assertThat(threadsAfterRateLimit)
                .hasSize(5)
                .noneMatch(name -> name.startsWith("lettuce-") && name.contains("EventLoop"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ThreadRecorder {

        @Bean
        List<String> threadsAfterRateLimit() {
            return new CopyOnWriteArrayList<>();
        }

        /** 라우트 필터(레이트 리밋) 뒤, 서비스로 보내기(NettyRoutingFilter) 바로 앞. */
        @Bean
        @Order(Ordered.LOWEST_PRECEDENCE - 1)
        GlobalFilter recordThread(List<String> threadsAfterRateLimit) {
            return (exchange, chain) -> {
                threadsAfterRateLimit.add(Thread.currentThread().getName());
                return chain.filter(exchange);
            };
        }
    }
}
