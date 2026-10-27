package com.openmarket.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.GenericContainer;

/**
 * Redis와 연결이 끊긴 동안 레이트 리밋 명령을 쌓아 두지 않는다. Lettuce 기본값은 끊긴 동안 명령을 무제한 버퍼에 쌓았다가 다시 연결되면 보낸다. 요청마다 명령
 * 타임아웃을 기다리고, 요청이 몰리면 쌓인 명령으로 힙이 찼다(docs/architecture/risks.md "장애 주입").
 *
 * <p>{@link RedisOutageTest}는 연결은 살아 있고 응답만 없는 경우다. 여기서는 컨테이너를 멈춰 연결을 끊는다. 컨텍스트는 따로 쓰고 버린다.
 */
@DirtiesContext
class RedisDisconnectTest extends GatewayTestSupport {

    private static final String PATH = "/api/member/v1/members/me";
    private static final int REQUESTS = 20;

    @Autowired GenericContainer<?> redis;

    @Test
    @DisplayName("Redis와 연결이 끊기면 레이트 리밋 명령을 쌓지 않고 바로 통과시킨다(명령 타임아웃을 기다리지 않는다)")
    void passesThroughWithoutBufferingWhenDisconnected() throws Exception {
        String token = TestJwt.token("account-1", "BUYER");
        getWithToken(PATH, token); // Redis 연결을 미리 맺는다
        redis.stop();

        long start = System.nanoTime();
        for (int i = 0; i < REQUESTS; i++) {
            assertThat(getWithToken(PATH, token).statusCode()).isEqualTo(200);
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        // 명령을 쌓으면 요청마다 명령 타임아웃(200ms)을 기다려 4초를 넘는다.
        assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
    }
}
