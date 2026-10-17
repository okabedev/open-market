package com.openmarket.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** 서비스 로그가 OpenTelemetry 로그 레코드로 나가고, 스팬 안에서 남긴 로그에는 그 트레이스가 붙는다. */
@SpringBootTest
@AutoConfigureTracing
@Import(LogExportIntegrationTest.Exporter.class)
class LogExportIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(LogExportIntegrationTest.class);

    @Autowired InMemoryLogRecordExporter exporter;

    @Autowired Tracer tracer;

    @Test
    @DisplayName("스팬 안에서 남긴 로그는 본문, 심각도, 로거 이름, 그 스팬의 trace id와 함께 나간다")
    void exportsLogWithTraceContext() {
        String message = "로그 내보내기 확인 " + UUID.randomUUID();
        Span span = tracer.nextSpan().name("test").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            log.warn(message);
        } finally {
            span.end();
        }

        LogRecordData record = find(message);
        assertThat(record.getSeverity()).isEqualTo(Severity.WARN);
        assertThat(record.getInstrumentationScopeInfo().getName())
                .isEqualTo(LogExportIntegrationTest.class.getName());
        assertThat(record.getSpanContext().getTraceId()).isEqualTo(span.context().traceId());
        assertThat(record.getSpanContext().getSpanId()).isEqualTo(span.context().spanId());
    }

    @Test
    @DisplayName("스팬 밖에서 남긴 로그도 트레이스 없이 나간다")
    void exportsLogWithoutTrace() {
        String message = "트레이스 없는 로그 " + UUID.randomUUID();

        log.info(message);

        assertThat(find(message).getSpanContext().isValid()).isFalse();
    }

    @Test
    @DisplayName("어펜더는 루트 로거에 하나만 붙는다")
    void attachesSingleAppenderToRoot() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);

        List<?> appenders =
                StreamSupport.stream(
                                java.util.Spliterators.spliteratorUnknownSize(
                                        root.iteratorForAppenders(), 0),
                                false)
                        .filter(OpenTelemetryAppender.class::isInstance)
                        .toList();

        assertThat(appenders).hasSize(1);
    }

    private LogRecordData find(String message) {
        List<LogRecordData> records =
                exporter.getFinishedLogRecordItems().stream()
                        .filter(r -> message.equals(r.getBodyValue().asString()))
                        .toList();
        assertThat(records).hasSize(1);
        return records.get(0);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Exporter {

        @Bean
        InMemoryLogRecordExporter inMemoryLogRecordExporter() {
            return InMemoryLogRecordExporter.create();
        }

        /** 배치 없이 바로 내보내 테스트가 기다리지 않게 한다. */
        @Bean
        LogRecordProcessor inMemoryLogRecordProcessor(InMemoryLogRecordExporter exporter) {
            return SimpleLogRecordProcessor.create(exporter);
        }
    }
}
