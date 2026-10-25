package com.openmarket.notification;

import java.util.stream.Stream;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/** notification이 구독하는 토픽과 DLT. 테스트 Kafka는 토픽 자동 생성을 끈다. */
@TestConfiguration(proxyBeanMethods = false)
class NotificationTopics {

    static final String[] TOPICS = {
        "auth.account",
        "order.order",
        "fulfillment.shipment",
        "fulfillment.claim",
        "payment.payment",
        "member.seller",
        "catalog.product",
        "review.review",
        "settlement.settlement"
    };

    @Bean
    KafkaAdmin.NewTopics notificationTopics() {
        return new KafkaAdmin.NewTopics(
                Stream.of(TOPICS)
                        .flatMap(topic -> Stream.of(topic, topic + ".DLT"))
                        .map(topic -> TopicBuilder.name(topic).partitions(1).build())
                        .toArray(NewTopic[]::new));
    }
}
