package com.openmarket.fulfillment.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.fulfillment.command.application.FulfillmentFakes.FakePurchaseConfirmations;
import com.openmarket.fulfillment.command.application.FulfillmentFakes.FakeShipmentRepository;
import com.openmarket.fulfillment.command.application.FulfillmentFakes.RecordingOutbox;
import com.openmarket.fulfillment.command.domain.PurchaseConfirmed;
import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentInvalidStateException;
import com.openmarket.fulfillment.command.domain.ShipmentLine;
import com.openmarket.fulfillment.command.domain.TrackingInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 구매확정(docs/architecture/fulfillment.md "구매확정"). */
class PurchaseConfirmUseCasesTest {

    static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");

    final FakeShipmentRepository shipments = new FakeShipmentRepository();
    final FakePurchaseConfirmations confirmations = new FakePurchaseConfirmations();
    final RecordingOutbox outbox = new RecordingOutbox();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    final ConfirmPurchaseUseCase confirm =
            new ConfirmPurchaseUseCase(shipments, confirmations, outbox, clock);
    final AutoConfirmPurchaseUseCase autoConfirm =
            new AutoConfirmPurchaseUseCase(shipments, confirmations, outbox, clock);

    final UUID orderLineId = UUID.randomUUID();
    final Shipment shipment = shipments.save(shipment());

    Shipment shipment() {
        return Shipment.ready(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                19_900,
                List.of(
                        new ShipmentLine(
                                orderLineId,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "반팔 티셔츠",
                                List.of(),
                                UUID.randomUUID(),
                                19_900,
                                1,
                                19_900)),
                NOW,
                NOW);
    }

    /** 8일 전에 배송 완료돼 자동 확정 기한(7일)이 지났다. */
    void deliver() {
        Instant deliveredAt = NOW.minus(Duration.ofDays(8));
        shipment.accept(deliveredAt);
        shipment.ship(TrackingInfo.of("CJ_LOGISTICS", "123456789012"), deliveredAt);
        shipment.startTransit(deliveredAt);
        shipment.deliver(deliveredAt, Duration.ofDays(7));
    }

    @Test
    @DisplayName("구매자가 확정하면 PurchaseConfirmed(BUYER, 주문 항목)를 낸다. 다시 해도 한 번이다")
    void confirmsByBuyer() {
        deliver();

        confirm.execute(new ConfirmPurchaseCommand(shipment.buyerId(), shipment.sellerOrderId()));
        confirm.execute(new ConfirmPurchaseCommand(shipment.buyerId(), shipment.sellerOrderId()));

        assertThat(confirmations.confirmations).hasSize(1);
        assertThat(outbox.types()).containsExactly(PurchaseConfirmed.TYPE);
        assertThat(outbox.events.getFirst().aggregateType()).isEqualTo("fulfillment.shipment");
        assertThat(outbox.events.getFirst().aggregateId())
                .isEqualTo(shipment.sellerOrderId().toString());
        PurchaseConfirmed event = (PurchaseConfirmed) outbox.events.getFirst().payload();
        assertThat(event.mode()).isEqualTo("BUYER");
        assertThat(event.confirmedAt()).isEqualTo(NOW);
        assertThat(event.sellerId()).isEqualTo(shipment.sellerId());
        assertThat(event.lines())
                .containsExactly(
                        new PurchaseConfirmed.Line(
                                orderLineId,
                                shipment.lines().getFirst().productId(),
                                shipment.lines().getFirst().categoryId(),
                                19_900));
    }

    @Test
    @DisplayName("배송 완료 전 확정은 409, 남의 하위 주문은 404이고 이벤트가 없다")
    void rejects() {
        assertThatThrownBy(
                        () ->
                                confirm.execute(
                                        new ConfirmPurchaseCommand(
                                                shipment.buyerId(), shipment.sellerOrderId())))
                .isInstanceOf(ShipmentInvalidStateException.class);
        deliver();
        assertThatThrownBy(
                        () ->
                                confirm.execute(
                                        new ConfirmPurchaseCommand(
                                                UUID.randomUUID(), shipment.sellerOrderId())))
                .isInstanceOfSatisfying(
                        NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo("FULFILLMENT_NOT_FOUND"));
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("자동 확정은 기한이 지난 배송만 AUTO로 확정한다. 이미 확정했거나 배송 완료가 아니거나 기한 전이면 넘긴다")
    void confirmsAutomatically() {
        autoConfirm.execute(shipment.id());
        assertThat(outbox.events).isEmpty();

        deliver();
        autoConfirm.execute(shipment.id());
        autoConfirm.execute(shipment.id());
        confirm.execute(new ConfirmPurchaseCommand(shipment.buyerId(), shipment.sellerOrderId()));

        assertThat(outbox.types()).containsExactly(PurchaseConfirmed.TYPE);
        assertThat(((PurchaseConfirmed) outbox.events.getFirst().payload()).mode())
                .isEqualTo("AUTO");
    }

    @Test
    @DisplayName("확정 기한 전에는 자동 확정하지 않는다")
    void notDueYet() {
        shipment.accept(NOW);
        shipment.ship(TrackingInfo.of("CJ_LOGISTICS", "123456789012"), NOW);
        shipment.startTransit(NOW);
        shipment.deliver(NOW, Duration.ofDays(7));

        autoConfirm.execute(shipment.id());

        assertThat(outbox.events).isEmpty();
    }
}
