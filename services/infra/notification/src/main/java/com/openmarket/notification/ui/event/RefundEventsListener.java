package com.openmarket.notification.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.notification.command.application.NotifyCommand;
import com.openmarket.notification.command.application.NotifyUseCase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code payment.payment} 토픽에서 {@code RefundFailed}만 받아 관리자 인앱 알림을 만든다. 관리자가 환불 재시도 화면에서 다시 시도한다. 그
 * 밖의 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class RefundEventsListener {

    static final String LISTENER = "notification.refund-failed";

    private final IdempotentEventProcessor processor;
    private final NotifyUseCase notify;

    RefundEventsListener(IdempotentEventProcessor processor, NotifyUseCase notify) {
        this.processor = processor;
        this.notify = notify;
    }

    @KafkaListener(topics = "payment.payment", groupId = "notification")
    void on(ConsumerRecord<String, String> record) {
        if (!"RefundFailed".equals(type(record))) {
            return;
        }
        processor.process(
                LISTENER,
                record,
                RefundFailedMessage.class,
                event ->
                        notify.execute(
                                new NotifyCommand(
                                        event.id(),
                                        List.of(
                                                new NotifyCommand.Notice(
                                                        NotifyCommand.Kind.REFUND_FAILED,
                                                        NotifyCommand.Audience.ADMIN,
                                                        null,
                                                        Map.of("code", event.payload().code()),
                                                        NotifyCommand.Target.REFUND,
                                                        event.payload().refundId())))));
    }

    /** 소비 쪽 페이로드. */
    record RefundFailedMessage(UUID refundId, String code) {}

    private static String type(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        return type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
    }
}
