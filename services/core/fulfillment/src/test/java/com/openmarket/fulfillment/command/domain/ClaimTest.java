package com.openmarket.fulfillment.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 클레임(docs/architecture/fulfillment.md "클레임", "발송 전 취소"). */
class ClaimTest {

    static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");

    final UUID skuA = UUID.randomUUID();
    final UUID skuB = UUID.randomUUID();

    Shipment ready() {
        return Shipment.ready(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                49_700,
                List.of(line(skuA, 2, 19_900), line(skuB, 1, 9_900)),
                NOW,
                NOW);
    }

    static ShipmentLine line(UUID skuId, int quantity, long unitPrice) {
        return new ShipmentLine(
                UUID.randomUUID(),
                skuId,
                UUID.randomUUID(),
                "반팔 티셔츠",
                List.of("화이트", "M"),
                UUID.randomUUID(),
                unitPrice,
                quantity,
                unitPrice * quantity);
    }

    @Test
    @DisplayName("발송 전 취소는 배송을 CANCELLED로 바꾸고 하위 주문 금액 전액을 환불하는 승인된 클레임이다")
    void cancel() {
        Shipment shipment = ready();
        shipment.accept(NOW);

        Claim claim = Claim.cancel(shipment, NOW.plusSeconds(10));

        assertThat(shipment.status()).isEqualTo(ShipmentStatus.CANCELLED);
        assertThat(shipment.cancelledAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(claim.type()).isEqualTo(ClaimType.CANCEL);
        assertThat(claim.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.reason()).isEqualTo(ClaimReason.BUYER_CANCEL);
        assertThat(claim.fault()).isEqualTo(Fault.BUYER);
        assertThat(claim.refundAmount()).isEqualTo(49_700);
        assertThat(claim.shipmentId()).isEqualTo(shipment.id());
        assertThat(claim.sellerOrderId()).isEqualTo(shipment.sellerOrderId());
        assertThat(claim.requestedAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(claim.approvedAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(claim.isOngoing()).isTrue();
    }

    @Test
    @DisplayName("발송한 뒤에는 취소할 수 없다(409, 지금 배송 상태)")
    void cancelAfterShipped() {
        Shipment shipment = ready();
        shipment.accept(NOW);
        shipment.ship(TrackingInfo.of("CJ_LOGISTICS", "123456789012"), NOW);

        assertThatThrownBy(() -> Claim.cancel(shipment, NOW))
                .isInstanceOfSatisfying(
                        ShipmentInvalidStateException.class,
                        e ->
                                assertThat(e.getProperties())
                                        .containsEntry("shipmentStatus", "SHIPPED"));
        assertThat(shipment.status()).isEqualTo(ShipmentStatus.SHIPPED);
    }

    @Test
    @DisplayName("환불이 끝나면 COMPLETED가 된다. 다시 해도 바뀌지 않는다")
    void complete() {
        Claim claim = Claim.cancel(ready(), NOW);
        claim.recordRefundFailure("NOT_CANCELABLE_AMOUNT", NOW);
        assertThat(claim.refundFailureCode()).isEqualTo("NOT_CANCELABLE_AMOUNT");

        assertThat(claim.complete(NOW.plusSeconds(5))).isTrue();
        assertThat(claim.complete(NOW.plusSeconds(9))).isFalse();

        assertThat(claim.status()).isEqualTo(ClaimStatus.COMPLETED);
        assertThat(claim.completedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(claim.refundFailureCode()).isNull();
        assertThat(claim.isOngoing()).isFalse();
    }

    @Test
    @DisplayName("환불 실패는 승인된 클레임에만 남긴다. 끝난 클레임은 그대로다")
    void refundFailureAfterCompleted() {
        Claim claim = Claim.cancel(ready(), NOW);
        claim.complete(NOW);

        assertThat(claim.recordRefundFailure("NOT_CANCELABLE_AMOUNT", NOW)).isFalse();

        assertThat(claim.refundFailureCode()).isNull();
    }

    @Test
    @DisplayName("ClaimCompleted의 재입고 항목은 취소한 배송의 항목을 SKU별로 합친 것이다")
    void restockLines() {
        Shipment shipment =
                Shipment.ready(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        69_600,
                        List.of(line(skuA, 2, 19_900), line(skuB, 1, 9_900), line(skuA, 1, 19_900)),
                        NOW,
                        NOW);
        Claim claim = Claim.cancel(shipment, NOW);
        claim.complete(NOW.plusSeconds(1));

        ClaimCompleted event = ClaimCompleted.of(claim, shipment);

        assertThat(event.type()).isEqualTo("CANCEL");
        assertThat(event.refundAmount()).isEqualTo(69_600);
        assertThat(event.completedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(event.restockLines())
                .containsExactly(
                        new ClaimCompleted.RestockLine(skuA, 3),
                        new ClaimCompleted.RestockLine(skuB, 1));
    }
}
