package com.openmarket.common.web;

import com.openmarket.common.web.error.GlobalExceptionHandler;
import com.openmarket.common.web.idempotency.IdempotencyCleanupJob;
import com.openmarket.common.web.idempotency.IdempotencyFilter;
import com.openmarket.common.web.idempotency.IdempotencyInterceptor;
import com.openmarket.common.web.idempotency.IdempotencyStore;
import com.openmarket.common.web.idempotency.JdbcIdempotencyStore;
import com.openmarket.common.web.logging.RequestLoggingFilter;
import java.time.Clock;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Spring MVC 서비스에 공통 오류 응답, 요청 로깅, Idempotency-Key 처리를 붙인다. 서비스는 의존성만 추가하면 된다. */
@AutoConfiguration(
        afterName = {
            "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
            "com.openmarket.common.messaging.CommonMessagingAutoConfiguration"
        })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registration =
                new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(JdbcClient.class)
    static class IdempotencyConfiguration {

        @Bean
        @ConditionalOnMissingBean
        IdempotencyStore idempotencyStore(JdbcClient jdbcClient) {
            return new JdbcIdempotencyStore(jdbcClient);
        }

        @Bean
        FilterRegistrationBean<IdempotencyFilter> idempotencyFilter() {
            FilterRegistrationBean<IdempotencyFilter> registration =
                    new FilterRegistrationBean<>(new IdempotencyFilter());
            registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
            return registration;
        }

        @Bean
        WebMvcConfigurer idempotencyWebMvcConfigurer(IdempotencyStore store) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(new IdempotencyInterceptor(store));
                }
            };
        }

        /** 락 실행기는 common-messaging이 등록한다. */
        @Bean
        @ConditionalOnBean(LockingTaskExecutor.class)
        IdempotencyCleanupJob idempotencyCleanupJob(
                IdempotencyStore store,
                LockingTaskExecutor lockingTaskExecutor,
                ObjectProvider<Clock> clock) {
            return new IdempotencyCleanupJob(
                    store, lockingTaskExecutor, clock.getIfAvailable(Clock::systemUTC));
        }
    }
}
