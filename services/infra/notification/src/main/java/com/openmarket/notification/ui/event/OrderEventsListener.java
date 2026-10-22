package com.openmarket.notification.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.notification.command.application.NotifyInAppCommand;
import com.openmarket.notification.command.application.NotifyInAppUseCase;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code order.order} 토픽에서 {@code OrderPaid}만 받아 구매자에게 주문 완료, 판매자마다 새 주문 인앱 알림을 만든다. 그 밖의 이벤트는 처리
 * 기록 없이 넘긴다.
 */
@Component
class OrderEventsListener {

    static final String LISTENER = "notification.order-paid";

    private final IdempotentEventProcessor processor;
    private final NotifyInAppUseCase notifyInApp;

    OrderEventsListener(IdempotentEventProcessor processor, NotifyInAppUseCase notifyInApp) {
        this.processor = processor;
        this.notifyInApp = notifyInApp;
    }

    @KafkaListener(topics = "order.order", groupId = "notification")
    void on(ConsumerRecord<String, String> record) {
        if (!"OrderPaid".equals(type(record))) {
            return;
        }
        processor.process(
                LISTENER,
                record,
                OrderPaidMessage.class,
                event -> notifyInApp.execute(notices(event.id(), event.payload())));
    }

    private static NotifyInAppCommand notices(UUID eventId, OrderPaidMessage order) {
        List<NotifyInAppCommand.Notice> notices = new ArrayList<>();
        notices.add(
                new NotifyInAppCommand.Notice(
                        NotifyInAppCommand.Kind.ORDER_PAID,
                        NotifyInAppCommand.Audience.BUYER,
                        order.buyerId(),
                        Map.of("orderName", order.orderName(), "amount", order.amount() + ""),
                        NotifyInAppCommand.Target.ORDER,
                        order.orderId()));
        for (OrderPaidMessage.SellerOrder sellerOrder : order.sellerOrders()) {
            notices.add(
                    new NotifyInAppCommand.Notice(
                            NotifyInAppCommand.Kind.SELLER_ORDER_RECEIVED,
                            NotifyInAppCommand.Audience.SELLER,
                            sellerOrder.sellerId(),
                            Map.of(
                                    "productName",
                                    sellerOrder.lines().getFirst().productName(),
                                    "lineCount",
                                    sellerOrder.lines().size() + "",
                                    "amount",
                                    sellerOrder.amount() + ""),
                            NotifyInAppCommand.Target.SELLER_ORDER,
                            sellerOrder.sellerOrderId()));
        }
        return new NotifyInAppCommand(eventId, notices);
    }

    /** 소비 쪽 페이로드. 알림에 쓰는 필드만 둔다. 배송지는 받지 않는다. */
    record OrderPaidMessage(
            UUID orderId,
            UUID buyerId,
            long amount,
            String orderName,
            List<SellerOrder> sellerOrders) {

        record SellerOrder(UUID sellerOrderId, UUID sellerId, long amount, List<Line> lines) {}

        record Line(String productName) {}
    }

    private static String type(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        return type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
    }
}
