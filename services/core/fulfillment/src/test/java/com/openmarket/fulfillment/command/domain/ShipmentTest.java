package com.openmarket.fulfillment.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShipmentTest {

    static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    static final Duration SEVEN_DAYS = Duration.ofDays(7);
    static final TrackingInfo TRACKING = TrackingInfo.of("CJ_LOGISTICS", "123456789012");

    final UUID sellerId = UUID.randomUUID();

    static Shipment ready(UUID sellerId) {
        return Shipment.ready(
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
                                List.of("화이트", "M"),
                                UUID.randomUUID(),
                                19_900,
                                1,
                                19_900)),
                NOW,
                NOW);
    }

    @Test
    @DisplayName("결제된 하위 주문의 배송은 READY로 만들고 판매자만 처리한다")
    void ready_() {
        Shipment shipment = ready(sellerId);

        assertThat(shipment.status()).isEqualTo(ShipmentStatus.READY);
        assertThat(shipment.isHandledBy(sellerId)).isTrue();
        assertThat(shipment.isHandledBy(UUID.randomUUID())).isFalse();
        assertThat(shipment.trackingInfo()).isNull();
    }

    @Test
    @DisplayName("발주 확인하면 ACCEPTED가 된다. 다시 해도 바뀌지 않는다")
    void accept() {
        Shipment shipment = ready(sellerId);

        assertThat(shipment.accept(NOW)).isEqualTo(Shipment.Transition.CHANGED);
        assertThat(shipment.accept(NOW.plusSeconds(1))).isEqualTo(Shipment.Transition.UNCHANGED);

        assertThat(shipment.status()).isEqualTo(ShipmentStatus.ACCEPTED);
        assertThat(shipment.acceptedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("발주 확인 전에는 발송할 수 없다")
    void shipBeforeAccept() {
        Shipment shipment = ready(sellerId);

        assertThatThrownBy(() -> shipment.ship(TRACKING, NOW))
                .isInstanceOfSatisfying(
                        ShipmentInvalidStateException.class,
                        e -> {
                            assertThat(e.getCode()).isEqualTo("FULFILLMENT_INVALID_STATE");
                            assertThat(e.getProperties()).containsEntry("shipmentStatus", "READY");
                        });
        assertThat(shipment.status()).isEqualTo(ShipmentStatus.READY);
    }

    @Test
    @DisplayName("발송하면 송장을 남긴다. 같은 송장으로 다시 하면 그대로, 다른 송장이면 409다")
    void ship() {
        Shipment shipment = ready(sellerId);
        shipment.accept(NOW);

        assertThat(shipment.ship(TRACKING, NOW.plusSeconds(60)))
                .isEqualTo(Shipment.Transition.CHANGED);
        assertThat(shipment.ship(TrackingInfo.of("CJ_LOGISTICS", "123456789012"), NOW))
                .isEqualTo(Shipment.Transition.UNCHANGED);
        assertThatThrownBy(() -> shipment.ship(TrackingInfo.of("HANJIN", "9999999999"), NOW))
                .isInstanceOf(ShipmentInvalidStateException.class);

        assertThat(shipment.status()).isEqualTo(ShipmentStatus.SHIPPED);
        assertThat(shipment.trackingInfo()).isEqualTo(TRACKING);
        assertThat(shipment.shippedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("발송 뒤 발주 확인은 409다")
    void acceptAfterShip() {
        Shipment shipment = ready(sellerId);
        shipment.accept(NOW);
        shipment.ship(TRACKING, NOW);

        assertThatThrownBy(() -> shipment.accept(NOW))
                .isInstanceOf(ShipmentInvalidStateException.class);
    }

    @Test
    @DisplayName("시뮬레이터는 발송 → 배송 중 → 배송 완료로 한 단계씩 바꾸고, 맞지 않는 상태면 넘긴다")
    void simulate() {
        Shipment shipment = ready(sellerId);

        assertThat(shipment.startTransit(NOW)).isFalse();
        assertThat(shipment.deliver(NOW, SEVEN_DAYS)).isFalse();

        shipment.accept(NOW);
        shipment.ship(TRACKING, NOW);
        assertThat(shipment.deliver(NOW, SEVEN_DAYS)).isFalse();
        assertThat(shipment.startTransit(NOW.plusSeconds(600))).isTrue();
        assertThat(shipment.startTransit(NOW.plusSeconds(601))).isFalse();
        assertThat(shipment.deliver(NOW.plusSeconds(4200), SEVEN_DAYS)).isTrue();
        assertThat(shipment.deliver(NOW.plusSeconds(4201), SEVEN_DAYS)).isFalse();

        assertThat(shipment.status()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(shipment.inTransitAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(shipment.deliveredAt()).isEqualTo(NOW.plusSeconds(4200));
        assertThat(shipment.confirmDueAt()).isEqualTo(NOW.plusSeconds(4200).plus(SEVEN_DAYS));
    }

    @Test
    @DisplayName("구매확정은 배송 완료된 배송에만 한다. 확정 방식과 시각을 남기고 하위 주문을 가리킨다")
    void confirm() {
        Shipment shipment = ready(sellerId);
        assertThatThrownBy(() -> PurchaseConfirmation.of(shipment, ConfirmMode.BUYER, NOW))
                .isInstanceOfSatisfying(
                        ShipmentInvalidStateException.class,
                        e ->
                                assertThat(e.getProperties())
                                        .containsEntry("shipmentStatus", "READY"));

        shipment.accept(NOW);
        shipment.ship(TRACKING, NOW);
        shipment.startTransit(NOW);
        shipment.deliver(NOW, SEVEN_DAYS);
        PurchaseConfirmation confirmation =
                PurchaseConfirmation.of(shipment, ConfirmMode.AUTO, NOW.plus(SEVEN_DAYS));

        assertThat(confirmation.sellerOrderId()).isEqualTo(shipment.sellerOrderId());
        assertThat(confirmation.shipmentId()).isEqualTo(shipment.id());
        assertThat(confirmation.mode()).isEqualTo(ConfirmMode.AUTO);
        assertThat(confirmation.confirmedAt()).isEqualTo(NOW.plus(SEVEN_DAYS));
    }

    @Test
    @DisplayName("구매자 본인의 배송인지 확인한다")
    void receivedBy() {
        Shipment shipment = ready(sellerId);

        assertThat(shipment.isReceivedBy(shipment.buyerId())).isTrue();
        assertThat(shipment.isReceivedBy(sellerId)).isFalse();
    }

    @Test
    @DisplayName("택배사는 정한 코드만, 송장 번호는 숫자 10~14자리만 받는다")
    void trackingInfo() {
        assertThat(TrackingInfo.of("KOREA_POST", "1234567890123").carrier())
                .isEqualTo(Carrier.KOREA_POST);
        for (String number : List.of("123456789", "123456789012345", "1234-567890", "")) {
            assertThatThrownBy(() -> TrackingInfo.of("CJ_LOGISTICS", number))
                    .isInstanceOfSatisfying(
                            InvalidFulfillmentInputException.class,
                            e -> assertThat(e.getCode()).isEqualTo("FULFILLMENT_INVALID_INPUT"));
        }
        assertThatThrownBy(() -> TrackingInfo.of("UNKNOWN", "1234567890"))
                .isInstanceOf(InvalidFulfillmentInputException.class);
    }
}
