package com.openmarket.catalog.ui.event;

import com.openmarket.catalog.command.application.RegisterSellerCommand;
import com.openmarket.catalog.command.application.RegisterSellerUseCase;
import com.openmarket.catalog.command.application.ReinstateSellerUseCase;
import com.openmarket.catalog.command.application.SellerStatusCommand;
import com.openmarket.catalog.command.application.SuspendSellerUseCase;
import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code member.seller} 토픽에서 {@code SellerApproved}(판매자 사본 생성), {@code SellerSuspended}(판매 중인 상품 모두
 * 중지), {@code SellerReinstated}(정지로 멈춘 상품 재개)를 받는다. 같은 토픽의 다른 이벤트는 처리 기록 없이 넘긴다. 키가 sellerId라 한
 * 판매자의 이벤트는 순서대로 온다.
 */
@Component
class SellerEventsListener {

    static final String SELLER_APPROVED = "SellerApproved";
    static final String SELLER_SUSPENDED = "SellerSuspended";
    static final String SELLER_REINSTATED = "SellerReinstated";

    private final IdempotentEventProcessor processor;
    private final RegisterSellerUseCase registerSeller;
    private final SuspendSellerUseCase suspendSeller;
    private final ReinstateSellerUseCase reinstateSeller;

    SellerEventsListener(
            IdempotentEventProcessor processor,
            RegisterSellerUseCase registerSeller,
            SuspendSellerUseCase suspendSeller,
            ReinstateSellerUseCase reinstateSeller) {
        this.processor = processor;
        this.registerSeller = registerSeller;
        this.suspendSeller = suspendSeller;
        this.reinstateSeller = reinstateSeller;
    }

    @KafkaListener(topics = "member.seller", groupId = "catalog")
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        String name = type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
        switch (name) {
            case SELLER_APPROVED ->
                    processor.process(
                            "catalog.seller-approved",
                            record,
                            SellerApprovedMessage.class,
                            event ->
                                    registerSeller.execute(
                                            new RegisterSellerCommand(
                                                    event.payload().sellerId(),
                                                    event.payload().storeName())));
            case SELLER_SUSPENDED ->
                    processor.process(
                            "catalog.seller-suspended",
                            record,
                            SellerStatusMessage.class,
                            event ->
                                    suspendSeller.execute(
                                            new SellerStatusCommand(event.payload().sellerId())));
            case SELLER_REINSTATED ->
                    processor.process(
                            "catalog.seller-reinstated",
                            record,
                            SellerStatusMessage.class,
                            event ->
                                    reinstateSeller.execute(
                                            new SellerStatusCommand(event.payload().sellerId())));
            default -> {}
        }
    }

    /** 소비 쪽 페이로드. 발행 쪽 타입을 쓰지 않는다(docs/architecture/messaging.md "스키마 규칙"). */
    record SellerApprovedMessage(UUID sellerId, String storeName) {}

    /** {@code SellerSuspended}, {@code SellerReinstated}. 정지 사유는 쓰지 않는다. */
    record SellerStatusMessage(UUID sellerId) {}
}
