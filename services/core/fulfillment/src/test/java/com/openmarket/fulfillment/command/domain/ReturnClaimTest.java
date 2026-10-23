package com.openmarket.fulfillment.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 반품·교환 신청, 판매자 승인·거부, 교환 재발송(docs/architecture/fulfillment.md "반품·교환 신청" 이하). */
class ReturnClaimTest {

    static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");
    static final long FEE = 6_000;

    final UUID sku = UUID.randomUUID();

    Shipment delivered(long amount) {
        Shipment shipment = ready(amount);
        shipment.accept(NOW);
        shipment.ship(TrackingInfo.of("CJ_LOGISTICS", "123456789012"), NOW);
        shipment.startTransit(NOW);
        shipment.deliver(NOW, Duration.ofDays(7));
        return shipment;
    }

    Shipment ready(long amount) {
        return Shipment.ready(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                amount,
                List.of(ClaimTest.line(sku, 2, amount / 2)),
                NOW,
                NOW);
    }

    /** 쿠폰 할인을 뺀 결제 금액이 상품 금액보다 작은 배송 완료 배송. */
    Shipment deliveredWithCoupon(long amount, long sellerCouponDiscount, long paymentAmount) {
        Shipment shipment =
                Shipment.ready(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        amount,
                        sellerCouponDiscount,
                        paymentAmount,
                        List.of(ClaimTest.line(sku, 2, amount / 2)),
                        NOW,
                        NOW);
        shipment.accept(NOW);
        shipment.ship(TrackingInfo.of("CJ_LOGISTICS", "123456789012"), NOW);
        shipment.startTransit(NOW);
        shipment.deliver(NOW, Duration.ofDays(7));
        return shipment;
    }

    Claim request(Shipment shipment, ClaimType type, ClaimReason reason) {
        return Claim.request(
                type, shipment, reason, "  사진처럼 찢어져 왔습니다.  ", List.of(UUID.randomUUID()), NOW);
    }

    @Test
    @DisplayName("반품·교환 신청은 배송 완료된 배송에 REQUESTED로 만든다. 귀책은 사유로 정하고 설명은 앞뒤 공백을 지운다")
    void requests() {
        Shipment shipment = delivered(39_800);
        UUID photo = UUID.randomUUID();

        Claim claim =
                Claim.request(
                        ClaimType.EXCHANGE,
                        shipment,
                        ClaimReason.DEFECTIVE,
                        " 불량 ",
                        List.of(photo),
                        NOW);

        assertThat(claim.status()).isEqualTo(ClaimStatus.REQUESTED);
        assertThat(claim.fault()).isEqualTo(Fault.SELLER);
        assertThat(claim.description()).isEqualTo("불량");
        assertThat(claim.fileIds()).containsExactly(photo);
        assertThat(claim.shipmentId()).isEqualTo(shipment.id());
        assertThat(claim.approvedAt()).isNull();
    }

    @Test
    @DisplayName("신청 규칙: 반품·교환만, 발송 전 취소 사유 안 됨, 교환은 판매자 귀책만, 설명 1~500자, 사진 5장까지 중복 없이")
    void requestRules() {
        Shipment shipment = delivered(39_800);

        assertInvalid(
                () ->
                        Claim.request(
                                ClaimType.CANCEL,
                                shipment,
                                ClaimReason.DEFECTIVE,
                                "x",
                                List.of(),
                                NOW));
        assertInvalid(() -> request(shipment, ClaimType.RETURN, ClaimReason.BUYER_CANCEL));
        assertInvalid(() -> request(shipment, ClaimType.EXCHANGE, ClaimReason.CHANGE_OF_MIND));
        assertInvalid(
                () ->
                        Claim.request(
                                ClaimType.RETURN,
                                shipment,
                                ClaimReason.DEFECTIVE,
                                "   ",
                                List.of(),
                                NOW));
        assertInvalid(
                () ->
                        Claim.request(
                                ClaimType.RETURN,
                                shipment,
                                ClaimReason.DEFECTIVE,
                                "가".repeat(501),
                                List.of(),
                                NOW));
        UUID photo = UUID.randomUUID();
        assertInvalid(
                () ->
                        Claim.request(
                                ClaimType.RETURN,
                                shipment,
                                ClaimReason.DEFECTIVE,
                                "x",
                                List.of(photo, photo),
                                NOW));
        assertInvalid(
                () ->
                        Claim.request(
                                ClaimType.RETURN,
                                shipment,
                                ClaimReason.DEFECTIVE,
                                "x",
                                List.of(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID()),
                                NOW));
        assertThat(
                        Claim.request(
                                        ClaimType.RETURN,
                                        shipment,
                                        ClaimReason.DEFECTIVE,
                                        "가".repeat(500),
                                        List.of(),
                                        NOW)
                                .fileIds())
                .isEmpty();
    }

    @Test
    @DisplayName("배송 완료 전에는 반품·교환을 신청할 수 없다(409, 지금 배송 상태)")
    void requestBeforeDelivered() {
        Shipment shipment = ready(39_800);

        assertThatThrownBy(() -> request(shipment, ClaimType.RETURN, ClaimReason.DEFECTIVE))
                .isInstanceOfSatisfying(
                        ShipmentInvalidStateException.class,
                        e ->
                                assertThat(e.getProperties())
                                        .containsEntry("shipmentStatus", "READY"));
    }

    @Test
    @DisplayName("쿠폰을 쓴 하위 주문의 반품은 결제 금액에서 계산한다. 구매자 귀책은 반품 배송비를 더 뺀다")
    void approveReturnWithCoupon() {
        Shipment shipment = deliveredWithCoupon(39_800, 2_000, 35_800);
        Claim buyerFault = request(shipment, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND);
        buyerFault.approve(shipment, true, FEE, NOW);
        assertThat(buyerFault.refundAmount()).isEqualTo(35_800 - FEE);

        Shipment other = deliveredWithCoupon(39_800, 2_000, 35_800);
        Claim sellerFault = request(other, ClaimType.RETURN, ClaimReason.DEFECTIVE);
        sellerFault.approve(other, false, FEE, NOW);
        assertThat(sellerFault.refundAmount()).isEqualTo(35_800);
    }

    @Test
    @DisplayName("구매자 귀책 반품은 환불에서 반품 배송비를 빼고(0 미만이면 0), 판매자 귀책은 전액이다. 다시 승인해도 그대로다")
    void approveReturn() {
        Shipment shipment = delivered(39_800);
        Claim buyerFault = request(shipment, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND);

        assertThat(buyerFault.approve(shipment, true, FEE, NOW.plusSeconds(1)))
                .isEqualTo(Claim.Approval.APPROVED);
        assertThat(buyerFault.approve(shipment, false, FEE, NOW.plusSeconds(2)))
                .isEqualTo(Claim.Approval.UNCHANGED);

        assertThat(buyerFault.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(buyerFault.refundAmount()).isEqualTo(33_800);
        assertThat(buyerFault.restock()).isTrue();
        assertThat(buyerFault.approvedAt()).isEqualTo(NOW.plusSeconds(1));

        Shipment cheap = delivered(5_000);
        Claim cheapReturn = request(cheap, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND);
        cheapReturn.approve(cheap, false, FEE, NOW);
        assertThat(cheapReturn.refundAmount()).isZero();

        Claim sellerFault = request(delivered(39_800), ClaimType.RETURN, ClaimReason.WRONG_ITEM);
        sellerFault.approve(shipment, false, FEE, NOW);
        assertThat(sellerFault.refundAmount()).isEqualTo(39_800);
        assertThat(sellerFault.restock()).isFalse();
    }

    @Test
    @DisplayName("반품 승인에는 재입고 여부가 필요하다")
    void approveReturnNeedsRestock() {
        Shipment shipment = delivered(39_800);
        Claim claim = request(shipment, ClaimType.RETURN, ClaimReason.DEFECTIVE);

        assertInvalid(() -> claim.approve(shipment, null, FEE, NOW));
        assertThat(claim.status()).isEqualTo(ClaimStatus.REQUESTED);
    }

    @Test
    @DisplayName("교환 승인은 환불 0이고 같은 항목·금액의 새 배송(READY)을 만든다. 새 배송이 하위 주문의 마지막 배송이다")
    void approveExchange() {
        Shipment shipment = delivered(39_800);
        Claim claim = request(shipment, ClaimType.EXCHANGE, ClaimReason.DEFECTIVE);

        assertThat(claim.approve(shipment, null, FEE, NOW.plusSeconds(1)))
                .isEqualTo(Claim.Approval.APPROVED);
        Shipment reshipment = claim.reship(shipment, NOW.plusSeconds(1));

        assertThat(claim.refundAmount()).isZero();
        assertThat(claim.restock()).isNull();
        assertThat(reshipment.status()).isEqualTo(ShipmentStatus.READY);
        assertThat(reshipment.claimId()).isEqualTo(claim.id());
        assertThat(reshipment.sellerOrderId()).isEqualTo(shipment.sellerOrderId());
        assertThat(reshipment.paymentAmount()).isEqualTo(shipment.paymentAmount());
        assertThat(reshipment.sellerCouponDiscount()).isEqualTo(shipment.sellerCouponDiscount());
        assertThat(reshipment.amount()).isEqualTo(39_800);
        assertThat(reshipment.lines())
                .usingRecursiveFieldByFieldElementComparator()
                .isEqualTo(shipment.lines());
        assertThat(reshipment.id()).isNotEqualTo(shipment.id());
    }

    @Test
    @DisplayName("거부는 사유(1~500자)를 남긴다. 다시 거부하면 그대로이고, 거부한 클레임은 승인할 수 없다(409, 지금 클레임 상태)")
    void reject() {
        Shipment shipment = delivered(39_800);
        Claim claim = request(shipment, ClaimType.RETURN, ClaimReason.DEFECTIVE);

        assertInvalid(() -> claim.reject(" ", NOW));
        assertThat(claim.reject(" 사용 흔적이 있습니다 ", NOW.plusSeconds(3))).isTrue();
        assertThat(claim.reject("다른 사유", NOW.plusSeconds(4))).isFalse();

        assertThat(claim.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(claim.rejectionReason()).isEqualTo("사용 흔적이 있습니다");
        assertThat(claim.rejectedAt()).isEqualTo(NOW.plusSeconds(3));
        assertThat(claim.isOngoing()).isFalse();
        assertThatThrownBy(() -> claim.approve(shipment, true, FEE, NOW))
                .isInstanceOfSatisfying(
                        ClaimInvalidStateException.class,
                        e -> {
                            assertThat(e.getCode()).isEqualTo("FULFILLMENT_CLAIM_INVALID_STATE");
                            assertThat(e.getProperties()).containsEntry("claimStatus", "REJECTED");
                        });
    }

    @Test
    @DisplayName("승인한 클레임은 거부할 수 없다")
    void rejectAfterApproved() {
        Shipment shipment = delivered(39_800);
        Claim claim = request(shipment, ClaimType.RETURN, ClaimReason.DEFECTIVE);
        claim.approve(shipment, true, FEE, NOW);

        assertThatThrownBy(() -> claim.reject("늦은 거부", NOW))
                .isInstanceOf(ClaimInvalidStateException.class);
    }

    @Test
    @DisplayName("거부되면 자동 구매확정 기한을 거부 시각 + 중재 요청 기한까지 늦춘다. 이미 더 늦으면 그대로다")
    void extendsConfirmDue() {
        Shipment shipment = delivered(39_800);
        Instant due = shipment.confirmDueAt();

        shipment.extendConfirmDue(NOW.plus(Duration.ofDays(1)));
        assertThat(shipment.confirmDueAt()).isEqualTo(due);
        shipment.extendConfirmDue(NOW.plus(Duration.ofDays(9)));
        assertThat(shipment.confirmDueAt()).isEqualTo(NOW.plus(Duration.ofDays(9)));
    }

    @Test
    @DisplayName("재입고를 고른 반품만 완료 이벤트에 재입고 항목이 있다. 교환은 없다")
    void restockLines() {
        Shipment shipment = delivered(39_800);
        Claim restocked = request(shipment, ClaimType.RETURN, ClaimReason.DEFECTIVE);
        restocked.approve(shipment, true, FEE, NOW);
        restocked.complete(NOW);
        Claim kept = request(shipment, ClaimType.RETURN, ClaimReason.DEFECTIVE);
        kept.approve(shipment, false, FEE, NOW);
        kept.complete(NOW);
        Claim exchange = request(shipment, ClaimType.EXCHANGE, ClaimReason.DEFECTIVE);
        exchange.approve(shipment, null, FEE, NOW);
        exchange.complete(NOW);

        assertThat(ClaimCompleted.of(restocked, shipment).restockLines())
                .containsExactly(new ClaimCompleted.RestockLine(sku, 2));
        assertThat(ClaimCompleted.of(kept, shipment).restockLines()).isEmpty();
        assertThat(ClaimCompleted.of(exchange, shipment).restockLines()).isEmpty();
        assertThat(ClaimCompleted.of(exchange, shipment).refundAmount()).isZero();
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        InvalidFulfillmentInputException.class,
                        e -> assertThat(e.getCode()).isEqualTo("FULFILLMENT_INVALID_INPUT"));
    }
}
