package com.openmarket.common.messaging;

import com.openmarket.common.messaging.cleanup.MessagingCleanupJob;
import com.openmarket.common.messaging.consumer.EventEnvelopeReader;
import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.common.messaging.outbox.JdbcOutbox;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.common.messaging.trace.MicrometerTraceparentSupplier;
import com.openmarket.common.messaging.trace.TraceparentSupplier;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Clock;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * outbox 기록, 멱등 소비, 정리 작업, ShedLock 락 실행기를 등록한다. 서비스는 의존성을 추가하고 Flyway 위치 {@code
 * classpath:db/common/messaging}을 더하면 된다.
 *
 * <p>스케줄링을 켠다. 서비스의 다른 스케줄 작업도 {@link LockingTaskExecutor}로 감싸 한 번만 실행되게 한다.
 */
@AutoConfiguration(
        afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration")
@ConditionalOnBean(JdbcClient.class)
@EnableScheduling
public class CommonMessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TraceparentSupplier traceparentSupplier(
            ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        return new MicrometerTraceparentSupplier(tracer, propagator);
    }

    @Bean
    @ConditionalOnMissingBean
    Outbox outbox(
            JdbcClient jdbcClient,
            ObjectProvider<JsonMapper> jsonMapper,
            TraceparentSupplier traceparentSupplier,
            ObjectProvider<Clock> clock) {
        return new JdbcOutbox(
                jdbcClient, jsonMapper(jsonMapper), traceparentSupplier, clock(clock));
    }

    @Bean
    @ConditionalOnMissingBean
    IdempotentEventProcessor idempotentEventProcessor(
            JdbcClient jdbcClient,
            ObjectProvider<JsonMapper> jsonMapper,
            PlatformTransactionManager transactionManager,
            ObjectProvider<Clock> clock) {
        return new IdempotentEventProcessor(
                new EventEnvelopeReader(jsonMapper(jsonMapper)),
                jdbcClient,
                new TransactionTemplate(transactionManager),
                clock(clock));
    }

    @Bean
    @ConditionalOnMissingBean
    LockProvider lockProvider(JdbcTemplate jdbcTemplate) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(jdbcTemplate)
                        .usingDbTime()
                        .build());
    }

    @Bean
    @ConditionalOnMissingBean
    LockingTaskExecutor lockingTaskExecutor(LockProvider lockProvider) {
        return new DefaultLockingTaskExecutor(lockProvider);
    }

    @Bean
    MessagingCleanupJob messagingCleanupJob(
            JdbcClient jdbcClient,
            LockingTaskExecutor lockingTaskExecutor,
            ObjectProvider<Clock> clock) {
        return new MessagingCleanupJob(jdbcClient, lockingTaskExecutor, clock(clock));
    }

    private static JsonMapper jsonMapper(ObjectProvider<JsonMapper> jsonMapper) {
        return jsonMapper.getIfAvailable(JsonMapper::shared);
    }

    private static Clock clock(ObjectProvider<Clock> clock) {
        return clock.getIfAvailable(Clock::systemUTC);
    }
}
