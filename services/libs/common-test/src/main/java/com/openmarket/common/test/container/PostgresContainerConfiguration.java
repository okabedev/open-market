package com.openmarket.common.test.container;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL 컨테이너를 띄우고 DataSource를 연결한다. 테스트에서 {@code @Import}로 쓴다.
 *
 * <p>컨테이너는 Spring 테스트 컨텍스트 하나당 하나다. 같은 설정의 테스트 클래스는 컨텍스트 캐시로 컨테이너를 같이 쓴다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(TestImages.POSTGRES);
    }
}
