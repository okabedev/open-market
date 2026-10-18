package com.openmarket.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.GenericContainer;

/**
 * Redis가 응답하지 않아도 레이트 리밋 때문에 요청이 막히거나 오래 걸리지 않는다.
 *
 * <p>컨테이너를 멈추면 연결이 바로 거부돼 문제가 드러나지 않는다. 일시 정지해서 연결은 살아 있고 응답만 없는 상태를 만든다. 컨텍스트는 따로 쓰고 버린다.
 */
@DirtiesContext
class RedisOutageTest extends GatewayTestSupport {

    private static final String PATH = "/api/member/v1/members/me";

    @Autowired GenericContainer<?> redis;

    @Test
    @DisplayName("Redis가 응답하지 않으면 레이트 리밋 없이 요청을 통과시키고 Redis 타임아웃 안에 응답한다")
    void passesThroughQuicklyWhenRedisHangs() throws Exception {
        String token = TestJwt.token("account-1", "BUYER");
        getWithToken(PATH, token); // Redis 연결을 미리 맺는다
        redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();

        long start = System.nanoTime();
        HttpResponse<String> response = getWithToken(PATH, token);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
    }
}
