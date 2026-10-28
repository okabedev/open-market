package com.openmarket.common.messaging;

import com.openmarket.common.messaging.consumer.FailedEventLogger;
import com.openmarket.common.messaging.consumer.InvalidEventException;
import com.openmarket.common.messaging.metrics.MessagingMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * 리스너 오류 처리기를 등록한다. Boot가 만드는 리스너 컨테이너 팩토리가 이 빈을 쓴다(docs/architecture/messaging.md "오류 처리").
 *
 * <ul>
 *   <li>일시 오류: 1초부터 2배씩 늘려 3회 재시도한다(1s, 2s, 4s). 그래도 실패하면 DLT로 보낸다.
 *   <li>영구 오류: {@link InvalidEventException}과 Spring Kafka가 재시도하지 않는 예외(역직렬화 실패 등)는 바로 DLT로 보낸다.
 * </ul>
 *
 * <p>DLT는 {@code <토픽>.DLT}의 같은 파티션이다. 원본 키, 값, 헤더를 그대로 두고 예외 정보 헤더({@code kafka_dlt-*})를 더한다. DLT
 * 전송이 실패하면 오프셋을 넘기지 않고 다시 처리한다. 실패와 DLT 전송은 {@link FailedEventLogger}가 로그로 남긴다.
 */
@AutoConfiguration(
        afterName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
public class KafkaConsumerAutoConfiguration {

    static final String DLT_SUFFIX = ".DLT";
    static final int MAX_RETRIES = 3;
    static final long INITIAL_INTERVAL_MS = 1_000;
    static final double MULTIPLIER = 2.0;

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    DefaultErrorHandler kafkaErrorHandler(
            KafkaOperations<?, ?> kafkaOperations,
            ObjectProvider<MessagingMetrics> metrics,
            ObjectProvider<MeterRegistry> registry) {
        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(MULTIPLIER);
        // spring-kafka 4 기본값은 <토픽>-dlt다. 선언한 토픽(infra/kafka/topics.txt)에 맞춰 이름을 정한다.
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaOperations,
                        (record, exception) ->
                                new TopicPartition(
                                        record.topic() + DLT_SUFFIX, record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class);
        // JDBC가 없어 CommonMessagingAutoConfiguration이 꺼진 앱도 DLT 유입을 센다.
        handler.setRetryListeners(
                new FailedEventLogger(
                        metrics.getIfAvailable(
                                () ->
                                        new MessagingMetrics(
                                                registry.getIfAvailable(
                                                        () -> Metrics.globalRegistry)))));
        return handler;
    }
}
