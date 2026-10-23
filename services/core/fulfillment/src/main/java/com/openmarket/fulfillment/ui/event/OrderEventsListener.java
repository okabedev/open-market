package com.openmarket.fulfillment.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.common.messaging.consumer.InvalidEventException;
import com.openmarket.fulfillment.command.application.CreateShipmentsCommand;
import com.openmarket.fulfillment.command.application.CreateShipmentsUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.Nullable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code order.order} 토픽에서 {@code OrderPaid}(배송 생성)를 받는다(docs/architecture/fulfillment.md "이벤트").
 * 같은 토픽의 다른 이벤트는 처리 기록 없이 넘긴다. 페이로드에 배송지가 있지만 fulfillment는 저장하지 않는다.
 *
 * <p>주문 항목 ID·상품명·옵션이 없는 M4 이전 형식의 {@code OrderPaid}는 배송을 만들 수 없다. 다시 시도해도 같으므로 영구 오류로 DLT에
 * 보낸다(docs/architecture/fulfillment.md "이벤트"). 쿠폰 필드가 없는 M5 이전 형식은 할인 0, 결제 금액 = 상품 금액으로 만든다.
 */
@Component
class OrderEventsListener {

    static final String ORDER_PAID = "OrderPaid";

    private final IdempotentEventProcessor processor;
    private final CreateShipmentsUseCase createShipments;

    OrderEventsListener(
            IdempotentEventProcessor processor, CreateShipmentsUseCase createShipments) {
        this.processor = processor;
        this.createShipments = createShipments;
    }

    @KafkaListener(topics = "order.order", groupId = "fulfillment")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        if (!ORDER_PAID.equals(name)) {
            return;
        }
        processor.process(
                "fulfillment.order-paid",
                record,
                PaidMessage.class,
                event -> createShipments.execute(event.payload().toCommand(event.id())));
    }

    /** 소비 쪽 페이로드. 주문 금액, 주문명, 배송지는 쓰지 않는다. */
    record PaidMessage(
            UUID orderId, UUID buyerId, Instant paidAt, List<SellerOrderMessage> sellerOrders) {

        CreateShipmentsCommand toCommand(UUID eventId) {
            boolean complete =
                    sellerOrders.stream()
                            .flatMap(sellerOrder -> sellerOrder.lines().stream())
                            .allMatch(LineMessage::complete);
            if (!complete) {
                throw new InvalidEventException(
                        "OrderPaid " + eventId + " 항목에 주문 항목 ID·상품명·옵션이 없다(M4 이전 형식)");
            }
            return new CreateShipmentsCommand(
                    orderId,
                    buyerId,
                    paidAt,
                    sellerOrders.stream().map(SellerOrderMessage::toCommand).toList());
        }
    }

    /** 쿠폰 필드(M5)는 M5 이전 형식에 없어 비어 있을 수 있다. 플랫폼 쿠폰 할인 몫은 결제 금액에 들어 있어 쓰지 않는다. */
    record SellerOrderMessage(
            UUID sellerOrderId,
            UUID sellerId,
            long amount,
            @Nullable Long sellerCouponDiscount,
            @Nullable Long paymentAmount,
            List<LineMessage> lines) {

        CreateShipmentsCommand.SellerOrder toCommand() {
            return new CreateShipmentsCommand.SellerOrder(
                    sellerOrderId,
                    sellerId,
                    amount,
                    sellerCouponDiscount == null ? 0 : sellerCouponDiscount,
                    paymentAmount == null ? amount : paymentAmount,
                    lines.stream()
                            .map(
                                    line ->
                                            new CreateShipmentsCommand.Line(
                                                    line.orderLineId(),
                                                    line.skuId(),
                                                    line.productId(),
                                                    line.productName(),
                                                    line.optionValues(),
                                                    line.categoryId(),
                                                    line.unitPrice(),
                                                    line.quantity(),
                                                    line.amount()))
                            .toList());
        }
    }

    record LineMessage(
            UUID orderLineId,
            UUID skuId,
            UUID productId,
            String productName,
            List<String> optionValues,
            UUID categoryId,
            long unitPrice,
            int quantity,
            long amount) {

        /** 역직렬화는 빠진 필드를 null로 채운다. M4에 더한 필드가 모두 있는가. */
        boolean complete() {
            return orderLineId != null && productName != null && optionValues != null;
        }
    }
}
