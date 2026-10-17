package com.openmarket.common.test.container;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Kafka 컨테이너(KRaft 단일 노드)를 띄우고 Spring Kafka 연결을 맞춘다. 테스트에서 {@code @Import}로 쓴다.
 *
 * <p>로컬 Compose처럼 토픽 자동 생성을 끈다. 테스트가 쓰는 토픽과 {@code .DLT}는 {@code NewTopic} 빈으로 만든다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaContainerConfiguration {

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(TestImages.KAFKA)
                .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
    }
}
