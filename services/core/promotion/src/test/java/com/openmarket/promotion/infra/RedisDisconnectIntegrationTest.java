package com.openmarket.promotion.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.test.container.RedisContainerConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.GenericContainer;

/**
 * Redis와 연결이 끊긴 동안 발급 게이트 명령을 쌓아 두지 않는다. Lettuce 기본값은 끊긴 동안 명령을 버퍼에 쌓았다가 다시 연결되면 보낸다. 그러면 요청마다 명령
 * 타임아웃(500ms)을 기다리고, 다시 연결되는 순간 쌓인 차지 스크립트를 한꺼번에 보내 발급이 10초 넘게 멈췄다. 호출자에게 이미 503을 준 차지가 나중에 Redis에서
 * 실행되기도 한다(docs/architecture/risks.md "장애 주입"). 컨텍스트는 따로 쓰고 버린다.
 */
@SpringBootTest(
        properties = {
            "spring.kafka.listener.auto-startup=false",
            // config-repo promotion.yml과 같다. 테스트 설정에는 없어 Lettuce 기본값(60초)이 된다.
            "spring.data.redis.timeout=500ms"
        })
@Import({PostgresContainerConfiguration.class, RedisContainerConfiguration.class})
@DirtiesContext
class RedisDisconnectIntegrationTest {

    private static final int CLAIMS = 10;

    @Autowired RedisCouponIssueGateAdapter gate;
    @Autowired GenericContainer<?> redisContainer;

    @Test
    @DisplayName("Redis와 연결이 끊기면 발급 게이트는 명령 타임아웃을 기다리지 않고 바로 503 PROMOTION_UNAVAILABLE이다")
    void failsFastWhenDisconnected() {
        UUID couponId = UUID.randomUUID();
        Instant expireAt = Instant.now().plus(Duration.ofHours(1));
        gate.claim(couponId, UUID.randomUUID(), 10, expireAt); // 연결을 미리 맺는다
        redisContainer.stop();

        long start = System.nanoTime();
        for (int i = 0; i < CLAIMS; i++) {
            assertThatThrownBy(() -> gate.claim(couponId, UUID.randomUUID(), 10, expireAt))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "PROMOTION_UNAVAILABLE");
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        // 명령을 쌓으면 차지마다 명령 타임아웃(500ms)을 기다려 5초를 넘는다.
        assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
    }
}
