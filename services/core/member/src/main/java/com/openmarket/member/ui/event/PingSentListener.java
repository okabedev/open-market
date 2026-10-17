package com.openmarket.member.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 샘플 이벤트 {@code PingSent}를 받아 로그만 남긴다(M0). 멱등 소비와 트레이스 연결을 확인한다. M1에서 지운다. */
@Component
class PingSentListener {

    static final String LISTENER = "member.ping-sent";

    private static final Logger log = LoggerFactory.getLogger(PingSentListener.class);

    private final IdempotentEventProcessor processor;

    PingSentListener(IdempotentEventProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = "member.ping", groupId = "member")
    void on(ConsumerRecord<String, String> record) {
        processor.process(
                LISTENER,
                record,
                Message.class,
                event -> log.info("샘플 이벤트를 처리했다: {} {}", event.type(), event.id()));
    }

    /**
     * 소비 쪽 페이로드. 발행 쪽 타입을 쓰지 않는다(docs/architecture/messaging.md "스키마 규칙").
     *
     * @param pingId 샘플 ID
     */
    record Message(UUID pingId) {}
}
