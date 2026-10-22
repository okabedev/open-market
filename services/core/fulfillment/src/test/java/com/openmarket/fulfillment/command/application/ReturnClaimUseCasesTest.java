package com.openmarket.fulfillment.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.fulfillment.command.application.FulfillmentFakes.FakeClaimRepository;
import com.openmarket.fulfillment.command.application.FulfillmentFakes.FakePurchaseConfirmations;
import com.openmarket.fulfillment.command.application.FulfillmentFakes.FakeShipmentRepository;
import com.openmarket.fulfillment.command.application.FulfillmentFakes.RecordingOutbox;
import com.openmarket.fulfillment.command.domain.Claim;
import com.openmarket.fulfillment.command.domain.ClaimApproved;
import com.openmarket.fulfillment.command.domain.ClaimCompleted;
import com.openmarket.fulfillment.command.domain.ClaimInProgressException;
import com.openmarket.fulfillment.command.domain.ClaimRejected;
import com.openmarket.fulfillment.command.domain.ClaimRequested;
import com.openmarket.fulfillment.command.domain.ClaimStatus;
import com.openmarket.fulfillment.command.domain.ConfirmMode;
import com.openmarket.fulfillment.command.domain.PurchaseConfirmation;
import com.openmarket.fulfillment.command.domain.PurchaseConfirmedException;
import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentLine;
import com.openmarket.fulfillment.command.domain.ShipmentShipped;
import com.openmarket.fulfillment.command.domain.ShipmentStatus;
import com.openmarket.fulfillment.command.domain.TrackingInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 반품·교환 신청, 판매자 승인·거부, 교환 재발송(docs/architecture/fulfillment.md "반품·교환 신청" 이하). */
class ReturnClaimUseCasesTest {

    static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");
    static final Duration MEDIATION_WINDOW = Duration.ofDays(7);

    final FakeShipmentRepository shipments = new FakeShipmentRepository();
    final FakeClaimRepository claims = new FakeClaimRepository();
    final FakePurchaseConfirmations confirmations = new FakePurchaseConfirmations();
    final RecordingOutbox outbox = new RecordingOutbox();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    final RequestClaimUseCase request =
            new RequestClaimUseCase(shipments, claims, confirmations, outbox, clock);
    final ApproveClaimUseCase approve =
            new ApproveClaimUseCase(claims, shipments, outbox, clock, 6_000);
    final RejectClaimUseCase reject =
            new RejectClaimUseCase(claims, shipments, outbox, clock, MEDIATION_WINDOW);
    final ShipShipmentUseCase ship = new ShipShipmentUseCase(shipments, claims, outbox, clock);

    final UUID skuId = UUID.randomUUID();
    final Shipment shipment = shipments.save(delivered());

    Shipment delivered() {
        Shipment shipment =
                Shipment.ready(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        39_800,
                        List.of(
                                new ShipmentLine(
                                        UUID.randomUUID(),
                                        skuId,
                                        UUID.randomUUID(),
                                        "반팔 티셔츠",
                                        List.of(),
                                        UUID.randomUUID(),
                                        19_900,
                                        2,
                                        39_800)),
                        NOW,
                        NOW);
        Instant deliveredAt = NOW.minus(Duration.ofDays(1));
        shipment.accept(deliveredAt);
        shipment.ship(TrackingInfo.of("CJ_LOGISTICS", "123456789012"), deliveredAt);
        shipment.startTransit(deliveredAt);
        shipment.deliver(deliveredAt, Duration.ofDays(7));
        return shipment;
    }

    UUID requestClaim(RequestClaimCommand.Type type, RequestClaimCommand.Reason reason) {
        return request.execute(
                new RequestClaimCommand(
                        shipment.buyerId(),
                        shipment.sellerOrderId(),
                        type,
                        reason,
                        "포장이 찢어져 왔습니다.",
                        List.of(UUID.randomUUID())));
    }

    @Test
    @DisplayName("반품 신청은 REQUESTED 클레임과 ClaimRequested(사진 포함)를 낸다. 진행 중이면 다시 신청할 수 없다")
    void requests() {
        UUID claimId =
                requestClaim(RequestClaimCommand.Type.RETURN, RequestClaimCommand.Reason.DEFECTIVE);

        assertThat(claims.claims)
                .singleElement()
                .satisfies(c -> assertThat(c.id()).isEqualTo(claimId));
        assertThat(outbox.types()).containsExactly(ClaimRequested.TYPE);
        ClaimRequested event = (ClaimRequested) outbox.events.getFirst().payload();
        assertThat(event.type()).isEqualTo("RETURN");
        assertThat(event.fault()).isEqualTo("SELLER");
        assertThat(event.fileIds()).hasSize(1);
        assertThatThrownBy(
                        () ->
                                requestClaim(
                                        RequestClaimCommand.Type.EXCHANGE,
                                        RequestClaimCommand.Reason.DEFECTIVE))
                .isInstanceOf(ClaimInProgressException.class);
    }

    @Test
    @DisplayName("구매확정한 하위 주문은 409, 남의 하위 주문은 404다")
    void requestRejected() {
        assertThatThrownBy(
                        () ->
                                request.execute(
                                        new RequestClaimCommand(
                                                UUID.randomUUID(),
                                                shipment.sellerOrderId(),
                                                RequestClaimCommand.Type.RETURN,
                                                RequestClaimCommand.Reason.DEFECTIVE,
                                                "x",
                                                List.of())))
                .isInstanceOf(NotFoundException.class);
        confirmations.save(PurchaseConfirmation.of(shipment, ConfirmMode.BUYER, NOW));
        assertThatThrownBy(
                        () ->
                                requestClaim(
                                        RequestClaimCommand.Type.RETURN,
                                        RequestClaimCommand.Reason.DEFECTIVE))
                .isInstanceOfSatisfying(
                        PurchaseConfirmedException.class,
                        e -> assertThat(e.getCode()).isEqualTo("FULFILLMENT_PURCHASE_CONFIRMED"));
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("구매자 귀책 반품을 승인하면 반품 배송비를 뺀 금액으로 ClaimApproved를 낸다. 다시 승인해도 한 번이다")
    void approvesReturn() {
        UUID claimId =
                requestClaim(
                        RequestClaimCommand.Type.RETURN, RequestClaimCommand.Reason.CHANGE_OF_MIND);
        outbox.events.clear();

        approve.execute(new ApproveClaimCommand(shipment.sellerId(), claimId, true));
        approve.execute(new ApproveClaimCommand(shipment.sellerId(), claimId, false));

        assertThat(outbox.types()).containsExactly(ClaimApproved.TYPE);
        ClaimApproved event = (ClaimApproved) outbox.events.getFirst().payload();
        assertThat(event.refundAmount()).isEqualTo(33_800);
        assertThat(event.fault()).isEqualTo("BUYER");
        assertThat(claims.claims.getFirst().restock()).isTrue();
    }

    @Test
    @DisplayName("다른 판매자와 발송 전 취소 클레임은 404 FULFILLMENT_CLAIM_NOT_FOUND다")
    void approveNotFound() {
        UUID claimId =
                requestClaim(RequestClaimCommand.Type.RETURN, RequestClaimCommand.Reason.DEFECTIVE);

        assertThatThrownBy(
                        () ->
                                approve.execute(
                                        new ApproveClaimCommand(UUID.randomUUID(), claimId, true)))
                .isInstanceOfSatisfying(
                        NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo("FULFILLMENT_CLAIM_NOT_FOUND"));

        Shipment ready =
                shipments.save(
                        Shipment.ready(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                shipment.sellerId(),
                                UUID.randomUUID(),
                                1_000,
                                shipment.lines(),
                                NOW,
                                NOW));
        Claim cancel = claims.save(Claim.cancel(ready, NOW));
        assertThatThrownBy(
                        () ->
                                reject.execute(
                                        new RejectClaimCommand(
                                                shipment.sellerId(), cancel.id(), "거부")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("교환을 승인하면 새 배송(READY)을 만든다. 새 배송을 발송하면 클레임이 끝나고 ClaimCompleted(재입고 없음)를 낸다")
    void exchanges() {
        UUID claimId =
                requestClaim(
                        RequestClaimCommand.Type.EXCHANGE, RequestClaimCommand.Reason.WRONG_ITEM);
        approve.execute(new ApproveClaimCommand(shipment.sellerId(), claimId, null));

        assertThat(shipments.shipments).hasSize(2);
        Shipment reshipment = shipments.shipments.getLast();
        assertThat(reshipment.status()).isEqualTo(ShipmentStatus.READY);
        assertThat(reshipment.claimId()).isEqualTo(claimId);
        assertThat(((ClaimApproved) outbox.events.getLast().payload()).refundAmount()).isZero();
        outbox.events.clear();

        reshipment.accept(NOW);
        ship.execute(
                new ShipShipmentCommand(
                        shipment.sellerId(), shipment.sellerOrderId(), "HANJIN", "9876543210"));

        assertThat(claims.claims.getFirst().status()).isEqualTo(ClaimStatus.COMPLETED);
        assertThat(outbox.types()).containsExactly(ShipmentShipped.TYPE, ClaimCompleted.TYPE);
        ClaimCompleted completed = (ClaimCompleted) outbox.events.getLast().payload();
        assertThat(completed.type()).isEqualTo("EXCHANGE");
        assertThat(completed.restockLines()).isEmpty();
    }

    @Test
    @DisplayName("거부하면 ClaimRejected(final=false)를 내고 자동 구매확정 기한을 거부 시각 + 7일로 늦춘다. 다시 거부해도 한 번이다")
    void rejects() {
        UUID claimId =
                requestClaim(RequestClaimCommand.Type.RETURN, RequestClaimCommand.Reason.DEFECTIVE);
        outbox.events.clear();

        reject.execute(new RejectClaimCommand(shipment.sellerId(), claimId, "사용 흔적이 있습니다"));
        reject.execute(new RejectClaimCommand(shipment.sellerId(), claimId, "사용 흔적이 있습니다"));

        assertThat(outbox.types()).containsExactly(ClaimRejected.TYPE);
        ClaimRejected event = (ClaimRejected) outbox.events.getFirst().payload();
        assertThat(event.mediated()).isFalse();
        assertThat(event.reason()).isEqualTo("사용 흔적이 있습니다");
        assertThat(shipment.confirmDueAt()).isEqualTo(NOW.plus(MEDIATION_WINDOW));

        requestClaim(RequestClaimCommand.Type.RETURN, RequestClaimCommand.Reason.DEFECTIVE);
        assertThat(claims.claims).hasSize(2);
    }
}
