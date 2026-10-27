package com.openmarket.common.messaging;

import com.openmarket.common.messaging.deadletter.DeadLetterCollector;
import com.openmarket.common.messaging.deadletter.DeadLetterRecorder;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.KafkaListenerConfigUtils;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * DLT 수집기(ADR 0022). 서비스 이름({@code spring.application.name})으로 수집 그룹을 정하므로 이름이 있어야 켜진다. 리스너처럼
 * {@code spring.kafka.listener.auto-startup}을 따른다(Kafka 없이 도는 테스트에서는 띄우지 않는다).
 */
@AutoConfiguration(
        after = CommonMessagingAutoConfiguration.class,
        afterName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@ConditionalOnBean({JdbcClient.class, ConsumerFactory.class})
public class DeadLetterAutoConfiguration {

    @Bean
    DeadLetterRecorder deadLetterRecorder(
            JdbcClient jdbcClient,
            ObjectProvider<JsonMapper> jsonMapper,
            ObjectProvider<Clock> clock) {
        return new DeadLetterRecorder(
                jdbcClient,
                jsonMapper.getIfAvailable(JsonMapper::shared),
                clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnProperty("spring.application.name")
    @ConditionalOnBean(name = KafkaListenerConfigUtils.KAFKA_LISTENER_ENDPOINT_REGISTRY_BEAN_NAME)
    @SuppressWarnings("unchecked")
    DeadLetterCollector deadLetterCollector(
            KafkaListenerEndpointRegistry registry,
            ConsumerFactory<?, ?> consumerFactory,
            DeadLetterRecorder recorder,
            KafkaProperties kafkaProperties,
            Environment environment) {
        return new DeadLetterCollector(
                registry,
                (ConsumerFactory<String, String>) consumerFactory,
                recorder,
                environment.getRequiredProperty("spring.application.name"),
                kafkaProperties.getListener().isAutoStartup());
    }
}
