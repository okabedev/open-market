package com.openmarket.common.observability;

import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Logback 로그를 OpenTelemetry 로그 레코드로 넘긴다. 내보내는 곳은 Boot 설정 {@code
 * management.opentelemetry.logging.export.otlp.endpoint}가 정한다(docs/architecture/observability.md).
 *
 * <p>Boot는 OpenTelemetry Logback 어펜더를 포함하지 않으므로, 서비스마다 {@code logback-spring.xml}을 두지 않고 여기서 루트 로거에
 * 붙인다.
 */
@AutoConfiguration(
        afterName =
                "org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration")
@ConditionalOnClass({OpenTelemetryAppender.class, LoggerContext.class})
public class LogExportAutoConfiguration {

    @Bean
    @ConditionalOnBean(OpenTelemetry.class)
    LogbackOpenTelemetryBridge logbackOpenTelemetryBridge(OpenTelemetry openTelemetry) {
        return new LogbackOpenTelemetryBridge(openTelemetry);
    }
}
