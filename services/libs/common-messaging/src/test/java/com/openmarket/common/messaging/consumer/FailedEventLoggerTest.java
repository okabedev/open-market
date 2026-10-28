package com.openmarket.common.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.common.messaging.metrics.MessagingMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaUtils;

class FailedEventLoggerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final FailedEventLogger logger = new FailedEventLogger(new MessagingMetrics(registry));

    @AfterEach
    void clearGroup() {
        KafkaUtils.clearConsumerGroupId();
    }

    @Test
    @DisplayName("DLT로 보내면 원래 토픽과 리스너 그룹으로 센다. 재시도 실패는 세지 않는다")
    void countsRecoveredRecords() {
        KafkaUtils.setConsumerGroupId("catalog");
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order.order", 0, 7, "k", "v");

        logger.failedDelivery(record, new IllegalStateException(), 1);
        logger.recovered(record, new IllegalStateException());

        assertThat(
                        registry.get(MessagingMetrics.DEAD_LETTERS)
                                .tag("topic", "order.order")
                                .tag("group", "catalog")
                                .counter()
                                .count())
                .isEqualTo(1);
    }
}
