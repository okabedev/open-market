package com.openmarket.common.test.container;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;

/** Redis 컨테이너를 띄우고 Spring Data Redis 연결을 맞춘다. 테스트에서 {@code @Import}로 쓴다. */
@TestConfiguration(proxyBeanMethods = false)
public class RedisContainerConfiguration {

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(TestImages.REDIS).withExposedPorts(6379);
    }
}
