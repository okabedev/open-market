package com.openmarket.fulfillment.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.fulfillment.command.application.FulfillmentFakes.FakeShipmentRepository;
import com.openmarket.fulfillment.command.application.FulfillmentFakes.RecordingOutbox;
import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentAccepted;
import com.openmarket.fulfillment.command.domain.ShipmentDelivered;
import com.openmarket.fulfillment.command.domain.ShipmentInTransit;
import com.openmarket.fulfillment.command.domain.ShipmentInvalidStateException;
import com.openmarket.fulfillment.command.domain.ShipmentLine;
import com.openmarket.fulfillment.command.domain.ShipmentShipped;
import com.openmarket.fulfillment.command.domain.ShipmentStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 발주 확인, 발송, 배송 시뮬레이터(docs/architecture/fulfillment.md "발주 확인과 발송", "배송 시뮬레이터"). */
class ShippingUseCasesTest {

    static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");

    final FakeShipmentRepository shipments = new FakeShipmentRepository();
    final RecordingOutbox outbox = new RecordingOutbox();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final UUID sellerId = UUID.randomUUID();

    final AcceptShipmentUseCase accept = new AcceptShipmentUseCase(shipments, outbox, clock);
    final ShipShipmentUseCase ship =
            new ShipShipmentUseCase(
                    shipments, new FulfillmentFakes.FakeClaimRepository(), outbox, clock);
    final StartTransitUseCase startTransit = new StartTransitUseCase(shipments, outbox, clock);
    final DeliverShipmentUseCase deliver =
            new DeliverShipmentUseCase(shipments, outbox, clock, Duration.ofDays(7));

    Shipment shipment;

    @BeforeEach
    void setUp() {
        shipment =
                shipments.save(
                        Shipment.ready(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                sellerId,
                                UUID.randomUUID(),
                                19_900,
                                List.of(
                                        new ShipmentLine(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                "반팔 티셔츠",
                                                List.of(),
                                                UUID.randomUUID(),
                                                19_900,
                                                1,
                                                19_900)),
                                NOW,
                                NOW));
    }

    @Test
    @DisplayName("발주 확인하면 ShipmentAccepted를 하위 주문 키로 낸다. 다시 해도 이벤트는 하나다")
    void accepts() {
        accept.execute(new AcceptShipmentCommand(sellerId, shipment.sellerOrderId()));
        accept.execute(new AcceptShipmentCommand(sellerId, shipment.sellerOrderId()));

        assertThat(shipment.status()).isEqualTo(ShipmentStatus.ACCEPTED);
        assertThat(outbox.types()).containsExactly(ShipmentAccepted.TYPE);
        assertThat(outbox.events.getFirst().aggregateType()).isEqualTo("fulfillment.shipment");
        assertThat(outbox.events.getFirst().aggregateId())
                .isEqualTo(shipment.sellerOrderId().toString());
        assertThat((ShipmentAccepted) outbox.events.getFirst().payload())
                .isEqualTo(
                        new ShipmentAccepted(
                                shipment.id(), shipment.orderId(), shipment.sellerOrderId(), NOW));
    }

    @Test
    @DisplayName("다른 판매자나 배송이 없는 하위 주문은 404 FULFILLMENT_NOT_FOUND")
    void notFound() {
        assertThatThrownBy(
                        () ->
                                accept.execute(
                                        new AcceptShipmentCommand(
                                                UUID.randomUUID(), shipment.sellerOrderId())))
                .isInstanceOfSatisfying(
                        NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo("FULFILLMENT_NOT_FOUND"));
        assertThatThrownBy(
                        () ->
                                ship.execute(
                                        new ShipShipmentCommand(
                                                sellerId,
                                                UUID.randomUUID(),
                                                "CJ_LOGISTICS",
                                                "123456789012")))
                .isInstanceOf(NotFoundException.class);
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("발송하면 송장과 함께 ShipmentShipped를 낸다. 같은 송장으로 다시 하면 이벤트가 없다")
    void ships() {
        accept.execute(new AcceptShipmentCommand(sellerId, shipment.sellerOrderId()));
        ShipShipmentCommand command =
                new ShipShipmentCommand(
                        sellerId, shipment.sellerOrderId(), "CJ_LOGISTICS", "123456789012");

        ship.execute(command);
        ship.execute(command);

        assertThat(outbox.types()).containsExactly(ShipmentAccepted.TYPE, ShipmentShipped.TYPE);
        assertThat((ShipmentShipped) outbox.events.getLast().payload())
                .isEqualTo(
                        new ShipmentShipped(
                                shipment.id(),
                                shipment.orderId(),
                                shipment.sellerOrderId(),
                                sellerId,
                                shipment.buyerId(),
                                "CJ_LOGISTICS",
                                "123456789012",
                                NOW));
    }

    @Test
    @DisplayName("발주 확인 전 발송은 409이고 이벤트가 없다")
    void shipBeforeAccept() {
        assertThatThrownBy(
                        () ->
                                ship.execute(
                                        new ShipShipmentCommand(
                                                sellerId,
                                                shipment.sellerOrderId(),
                                                "CJ_LOGISTICS",
                                                "123456789012")))
                .isInstanceOf(ShipmentInvalidStateException.class);
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("시뮬레이터 단계는 맞는 상태에서만 바꾸고 이벤트를 낸다. 다시 불러도 한 번이다")
    void simulates() {
        accept.execute(new AcceptShipmentCommand(sellerId, shipment.sellerOrderId()));
        ship.execute(
                new ShipShipmentCommand(
                        sellerId, shipment.sellerOrderId(), "HANJIN", "1234567890"));

        deliver.execute(shipment.id());
        startTransit.execute(shipment.id());
        startTransit.execute(shipment.id());
        deliver.execute(shipment.id());
        deliver.execute(shipment.id());

        assertThat(shipment.status()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(outbox.types())
                .containsExactly(
                        ShipmentAccepted.TYPE,
                        ShipmentShipped.TYPE,
                        ShipmentInTransit.TYPE,
                        ShipmentDelivered.TYPE);
        assertThat((ShipmentDelivered) outbox.events.getLast().payload())
                .isEqualTo(
                        new ShipmentDelivered(
                                shipment.id(),
                                shipment.orderId(),
                                shipment.sellerOrderId(),
                                shipment.buyerId(),
                                NOW));
    }
}
