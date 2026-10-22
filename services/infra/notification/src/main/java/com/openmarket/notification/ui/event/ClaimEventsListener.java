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
import org.jspecify.annotations.Nullable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code fulfillment.claim} 토픽의 클레임 이벤트로 인앱 알림을 만든다(docs/architecture/notification.md "인앱 알림").
 *
 * <ul>
 *   <li>{@code ClaimRequested} → 판매자(취소 포함)
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
    private final NotifyInAppUseCase notifyInApp;

    ClaimEventsListener(IdempotentEventProcessor processor, NotifyInAppUseCase notifyInApp) {
        this.processor = processor;
        this.notifyInApp = notifyInApp;
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
                                    notifyInApp.execute(
                                            one(
                                                    event.id(),
                                                    NotifyInAppCommand.Kind.CLAIM_REQUESTED,
                                                    NotifyInAppCommand.Audience.SELLER,
                                                    event.payload().sellerId(),
                                                    Map.of("type", event.payload().type()),
                                                    NotifyInAppCommand.Target.CLAIM,
                                                    event.payload().claimId())));
            case "ClaimApproved" ->
                    processor.process(
                            "notification.claim-approved",
                            record,
                            ClaimApprovedMessage.class,
                            event -> {
                                if (!"CANCEL".equals(event.payload().type())) {
                                    notifyInApp.execute(
                                            toBuyer(
                                                    event.id(),
                                                    NotifyInAppCommand.Kind.CLAIM_APPROVED,
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
                                    notifyInApp.execute(
                                            toBuyer(
                                                    event.id(),
                                                    NotifyInAppCommand.Kind.CLAIM_REJECTED,
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
                                    notifyInApp.execute(
                                            toBuyer(
                                                    event.id(),
                                                    NotifyInAppCommand.Kind.CLAIM_COMPLETED,
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
                                    notifyInApp.execute(
                                            one(
                                                    event.id(),
                                                    NotifyInAppCommand.Kind
                                                            .CLAIM_MEDIATION_REQUESTED,
                                                    NotifyInAppCommand.Audience.ADMIN,
                                                    null,
                                                    Map.of("type", event.payload().type()),
                                                    NotifyInAppCommand.Target.CLAIM,
                                                    event.payload().claimId())));
            default -> {}
        }
    }

    private static NotifyInAppCommand toBuyer(
            UUID eventId,
            NotifyInAppCommand.Kind kind,
            UUID buyerId,
            UUID orderId,
            Map<String, String> params) {
        return one(
                eventId,
                kind,
                NotifyInAppCommand.Audience.BUYER,
                buyerId,
                params,
                NotifyInAppCommand.Target.ORDER,
                orderId);
    }

    private static NotifyInAppCommand one(
            UUID eventId,
            NotifyInAppCommand.Kind kind,
            NotifyInAppCommand.Audience audience,
            @Nullable UUID recipientId,
            Map<String, String> params,
            NotifyInAppCommand.Target target,
            UUID targetId) {
        return new NotifyInAppCommand(
                eventId,
                List.of(
                        new NotifyInAppCommand.Notice(
                                kind, audience, recipientId, params, target, targetId)));
    }

    /** 소비 쪽 페이로드. 알림에 쓰는 필드만 둔다. */
    record ClaimRequestedMessage(UUID claimId, String type, UUID sellerId) {}

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
