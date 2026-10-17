package com.openmarket.common.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * 루트 로거에 {@link OpenTelemetryAppender}를 붙이고 OpenTelemetry 인스턴스를 넘긴다.
 *
 * <p>어펜더는 현재 스팬의 trace id·span id를 레코드에 넣는다. 이 빈이 만들어지기 전의 기동 로그(설정 읽기, Flyway 등)는 표준 출력에만 남는다.
 * Spring 로깅 시스템이 Logback을 다시 초기화하면 어펜더가 빠지므로, 컨텍스트가 뜰 때마다 없으면 다시 붙인다.
 */
class LogbackOpenTelemetryBridge implements InitializingBean {

    static final String APPENDER_NAME = "OTEL";

    private final OpenTelemetry openTelemetry;

    LogbackOpenTelemetryBridge(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void afterPropertiesSet() {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            if (root.getAppender(APPENDER_NAME) == null) {
                OpenTelemetryAppender appender = new OpenTelemetryAppender();
                appender.setName(APPENDER_NAME);
                appender.setContext(context);
                appender.start();
                root.addAppender(appender);
            }
        }
        OpenTelemetryAppender.install(openTelemetry);
    }
}
