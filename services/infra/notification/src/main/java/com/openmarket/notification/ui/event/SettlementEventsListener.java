package com.openmarket.notification.ui.event;

import com.openmarket.common.messaging.consumer.IdempotentEventProcessor;
import com.openmarket.notification.command.application.NotifyCommand;
import com.openmarket.notification.command.application.NotifyUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code settlement.settlement} 토픽의 정산 확정({@code SettlementConfirmed})과 지급({@code
 * SettlementPaidOut})으로 판매자에게 알림을 만든다(NTF-1 정산 확정). 둘 다 이메일도 보낸다. 그 밖의 이벤트는 처리 기록 없이 넘긴다.
 */
@Component
class SettlementEventsListener {

    private final IdempotentEventProcessor processor;
    private final NotifyUseCase notify;

    SettlementEventsListener(IdempotentEventProcessor processor, NotifyUseCase notify) {
        this.processor = processor;
        this.notify = notify;
    }

    @KafkaListener(topics = "settlement.settlement", groupId = "notification")
    void on(ConsumerRecord<String, String> record) {
        switch (type(record)) {
            case "SettlementConfirmed" ->
                    processor.process(
                            "notification.settlement-confirmed",
                            record,
                            SettlementConfirmedMessage.class,
                            event ->
                                    notify.execute(
                                            toSeller(
                                                    event.id(),
                                                    NotifyCommand.Kind.SETTLEMENT_CONFIRMED,
                                                    event.payload().settlementId(),
                                                    event.payload().sellerId(),
                                                    Map.of(
                                                            "periodStart",
                                                            event.payload().periodStart() + "",
                                                            "periodEnd",
                                                            event.payload().periodEnd() + "",
                                                            "amount",
                                                            event.payload().amount() + ""))));
            case "SettlementPaidOut" ->
                    processor.process(
                            "notification.settlement-paid-out",
                            record,
                            SettlementPaidOutMessage.class,
                            event ->
                                    notify.execute(
                                            toSeller(
                                                    event.id(),
                                                    NotifyCommand.Kind.SETTLEMENT_PAID_OUT,
                                                    event.payload().settlementId(),
                                                    event.payload().sellerId(),
                                                    Map.of(
                                                            "amount",
                                                            event.payload().amount() + ""))));
            default -> {}
        }
    }

    private static NotifyCommand toSeller(
            UUID eventId,
            NotifyCommand.Kind kind,
            UUID settlementId,
            UUID sellerId,
            Map<String, String> params) {
        return new NotifyCommand(
                eventId,
                List.of(
                        new NotifyCommand.Notice(
                                kind,
                                NotifyCommand.Audience.SELLER,
                                sellerId,
                                params,
                                NotifyCommand.Target.SETTLEMENT,
                                settlementId)));
    }

    /**
     * 소비 쪽 페이로드. 알림에 쓰는 필드만 둔다.
     *
     * @param periodEnd 기간 끝. 기간에 들지 않는다
     * @param amount 정산 금액(원)
     */
    record SettlementConfirmedMessage(
            UUID settlementId,
            UUID sellerId,
            Instant periodStart,
            Instant periodEnd,
            long amount) {}

    /** 소비 쪽 페이로드. */
    record SettlementPaidOutMessage(UUID settlementId, UUID sellerId, long amount) {}

    private static String type(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader("type");
        return type == null ? "" : new String(type.value(), StandardCharsets.UTF_8);
    }
}
