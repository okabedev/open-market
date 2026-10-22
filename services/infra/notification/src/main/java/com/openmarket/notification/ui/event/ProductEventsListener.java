package com.openmarket.notification.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.notification.command.application.NotifyInAppCommand;
import com.openmarket.notification.command.application.NotifyInAppUseCase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code catalog.product} 토픽에서 {@code ProductBanned}만 받아 판매자에게 인앱 알림을 만든다. 그 밖의 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class ProductEventsListener {

    static final String LISTENER = "notification.product-banned";

    private final IdempotentEventProcessor processor;
    private final NotifyInAppUseCase notifyInApp;

    ProductEventsListener(IdempotentEventProcessor processor, NotifyInAppUseCase notifyInApp) {
        this.processor = processor;
        this.notifyInApp = notifyInApp;
    }

    @KafkaListener(topics = "catalog.product", groupId = "notification")
    void on(ConsumerRecord<String, String> record) {
        if (!"ProductBanned".equals(type(record))) {
            return;
        }
        processor.process(
                LISTENER,
                record,
                ProductBannedMessage.class,
                event ->
                        notifyInApp.execute(
                                new NotifyInAppCommand(
                                        event.id(),
                                        List.of(
                                                new NotifyInAppCommand.Notice(
                                                        NotifyInAppCommand.Kind.PRODUCT_BANNED,
                                                        NotifyInAppCommand.Audience.SELLER,
                                                        event.payload().sellerId(),
                                                        Map.of("reason", event.payload().reason()),
                                                        NotifyInAppCommand.Target.PRODUCT,
                                                        event.payload().productId())))));
    }

    /** 소비 쪽 페이로드. */
    record ProductBannedMessage(UUID productId, UUID sellerId, String reason) {}

    private static String type(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        return type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
    }
}
