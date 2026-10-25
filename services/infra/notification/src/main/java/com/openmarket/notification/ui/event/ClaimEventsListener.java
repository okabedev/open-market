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
import org.jspecify.annotations.Nullable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code fulfillment.claim} 토픽의 클레임 이벤트로 인앱 알림을 만든다(docs/architecture/notification.md "인앱 알림").
 *
 * <ul>
 *   <li>{@code ClaimRequested} → 판매자(취소 포함). 하위 주문 상세로 간다
 *   <li>{@code ClaimApproved} → 구매자(반품·교환만. 취소는 신청과 함께 승인되므로 알리지 않고 처리 기록만 남긴다)
 *   <li>{@code ClaimRejected}, {@code ClaimCompleted} → 구매자
 *   <li>{@code ClaimMediationRequested} → 관리자
 * </ul>
 *
 * 그 밖의 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class ClaimEventsListener {

    private final IdempotentEventProcessor processor;
    private final NotifyUseCase notify;

    ClaimEventsListener(IdempotentEventProcessor processor, NotifyUseCase notify) {
        this.processor = processor;
        this.notify = notify;
    }

    @KafkaListener(topics = "fulfillment.claim", groupId = "notification")
    void on(ConsumerRecord<String, String> record) {
        switch (type(record)) {
            case "ClaimRequested" ->
                    processor.process(
                            "notification.claim-requested",
                            record,
                            ClaimRequestedMessage.class,
                            event ->
                                    notify.execute(
                                            one(
                                                    event.id(),
                                                    NotifyCommand.Kind.CLAIM_REQUESTED,
                                                    NotifyCommand.Audience.SELLER,
                                                    event.payload().sellerId(),
                                                    Map.of("type", event.payload().type()),
                                                    // 클레임은 하위 주문 상세에서 처리한다
                                                    NotifyCommand.Target.SELLER_ORDER,
                                                    event.payload().sellerOrderId())));
            case "ClaimApproved" ->
                    processor.process(
                            "notification.claim-approved",
                            record,
                            ClaimApprovedMessage.class,
                            event -> {
                                if (!"CANCEL".equals(event.payload().type())) {
                                    notify.execute(
                                            toBuyer(
                                                    event.id(),
                                                    NotifyCommand.Kind.CLAIM_APPROVED,
                                                    event.payload().buyerId(),
                                                    event.payload().orderId(),
                                                    Map.of(
                                                            "type",
                                                            event.payload().type(),
                                                            "refundAmount",
                                                            event.payload().refundAmount() + "")));
                                }
                            });
            case "ClaimRejected" ->
                    processor.process(
                            "notification.claim-rejected",
                            record,
                            ClaimRejectedMessage.class,
                            event ->
                                    notify.execute(
                                            toBuyer(
                                                    event.id(),
                                                    NotifyCommand.Kind.CLAIM_REJECTED,
                                                    event.payload().buyerId(),
                                                    event.payload().orderId(),
                                                    Map.of(
                                                            "type",
                                                            event.payload().type(),
                                                            "mediated",
                                                            event.payload().mediated() + ""))));
            case "ClaimCompleted" ->
                    processor.process(
                            "notification.claim-completed",
                            record,
                            ClaimCompletedMessage.class,
                            event ->
                                    notify.execute(
                                            toBuyer(
                                                    event.id(),
                                                    NotifyCommand.Kind.CLAIM_COMPLETED,
                                                    event.payload().buyerId(),
                                                    event.payload().orderId(),
                                                    Map.of(
                                                            "type",
                                                            event.payload().type(),
                                                            "refundAmount",
                                                            event.payload().refundAmount() + ""))));
            case "ClaimMediationRequested" ->
                    processor.process(
                            "notification.claim-mediation-requested",
                            record,
                            ClaimMediationRequestedMessage.class,
                            event ->
                                    notify.execute(
                                            one(
                                                    event.id(),
                                                    NotifyCommand.Kind.CLAIM_MEDIATION_REQUESTED,
                                                    NotifyCommand.Audience.ADMIN,
                                                    null,
                                                    Map.of("type", event.payload().type()),
                                                    NotifyCommand.Target.CLAIM,
                                                    event.payload().claimId())));
            default -> {}
        }
    }

    private static NotifyCommand toBuyer(
            UUID eventId,
            NotifyCommand.Kind kind,
            UUID buyerId,
            UUID orderId,
            Map<String, String> params) {
        return one(
                eventId,
                kind,
                NotifyCommand.Audience.BUYER,
                buyerId,
                params,
                NotifyCommand.Target.ORDER,
                orderId);
    }

    private static NotifyCommand one(
            UUID eventId,
            NotifyCommand.Kind kind,
            NotifyCommand.Audience audience,
            @Nullable UUID recipientId,
            Map<String, String> params,
            NotifyCommand.Target target,
            UUID targetId) {
        return new NotifyCommand(
                eventId,
                List.of(
                        new NotifyCommand.Notice(
                                kind, audience, recipientId, params, target, targetId)));
    }

    /** 소비 쪽 페이로드. 알림에 쓰는 필드만 둔다. */
    record ClaimRequestedMessage(String type, UUID sellerOrderId, UUID sellerId) {}

    /** 소비 쪽 페이로드. */
    record ClaimApprovedMessage(String type, UUID orderId, UUID buyerId, long refundAmount) {}

    /**
     * 소비 쪽 페이로드.
     *
     * @param mediated 중재 결과로 거부가 확정됐는가
     */
    record ClaimRejectedMessage(String type, UUID orderId, UUID buyerId, boolean mediated) {}

    /** 소비 쪽 페이로드. */
    record ClaimCompletedMessage(String type, UUID orderId, UUID buyerId, long refundAmount) {}

    /** 소비 쪽 페이로드. */
    record ClaimMediationRequestedMessage(UUID claimId, String type) {}

    private static String type(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        return type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
    }
}
