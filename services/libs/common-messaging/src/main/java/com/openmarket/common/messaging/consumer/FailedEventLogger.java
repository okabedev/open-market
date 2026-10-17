package com.openmarket.common.messaging.consumer;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.KafkaUtils;

/**
 * 리스너 실패, DLT 전송, DLT 전송 실패를 로그로 남긴다. Spring Kafka는 DLT로 보내는 데 성공하면 아무것도 남기지 않는다.
 *
 * <p>위치({@code 토픽-파티션@오프셋}), 이벤트 {@code type}·{@code id}, 예외 클래스만 쓴다. 키, 값, 예외 메시지는 개인정보가 들어갈 수 있어
 * 쓰지 않는다. 예외 메시지와 스택은 DLT 메시지의 {@code kafka_dlt-*} 헤더에 남는다.
 */
public class FailedEventLogger implements RetryListener {

    private static final Logger log = LoggerFactory.getLogger(FailedEventLogger.class);

    @Override
    public void failedDelivery(
            ConsumerRecord<?, ?> record, @Nullable Exception ex, int deliveryAttempt) {
        log.warn(
                "이벤트 처리 실패({}회째): {} {} {} - {}",
                deliveryAttempt,
                KafkaUtils.format(record),
                header(record, EventEnvelopeReader.TYPE),
                header(record, EventEnvelopeReader.ID),
                cause(ex));
    }

    @Override
    public void recovered(ConsumerRecord<?, ?> record, @Nullable Exception ex) {
        log.error(
                "이벤트를 DLT로 보냈다: {} {} {} - {}",
                KafkaUtils.format(record),
                header(record, EventEnvelopeReader.TYPE),
                header(record, EventEnvelopeReader.ID),
                cause(ex));
    }

    @Override
    public void recoveryFailed(
            ConsumerRecord<?, ?> record, @Nullable Exception original, Exception failure) {
        log.error(
                "이벤트를 DLT로 보내지 못해 다시 처리한다: {} {} {} - {}, 전송 오류 {}",
                KafkaUtils.format(record),
                header(record, EventEnvelopeReader.TYPE),
                header(record, EventEnvelopeReader.ID),
                cause(original),
                failure.getClass().getName());
    }

    private static String cause(@Nullable Exception ex) {
        if (ex == null) {
            return "-";
        }
        Throwable cause = ex;
        if (cause instanceof ListenerExecutionFailedException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getName();
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return "-";
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
