package com.openmarket.fulfillment.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.fulfillment.command.application.FulfillmentFakes.FakeClaimRepository;
import com.openmarket.fulfillment.command.application.FulfillmentFakes.FakeShipmentRepository;
import com.openmarket.fulfillment.command.application.FulfillmentFakes.RecordingOutbox;
import com.openmarket.fulfillment.command.domain.Claim;
import com.openmarket.fulfillment.command.domain.ClaimApproved;
import com.openmarket.fulfillment.command.domain.ClaimCompleted;
import com.openmarket.fulfillment.command.domain.ClaimInProgressException;
import com.openmarket.fulfillment.command.domain.ClaimRequested;
import com.openmarket.fulfillment.command.domain.ClaimStatus;
import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentInvalidStateException;
import com.openmarket.fulfillment.command.domain.ShipmentLine;
import com.openmarket.fulfillment.command.domain.ShipmentStatus;
import com.openmarket.fulfillment.command.domain.TrackingInfo;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 발송 전 취소와 환불 결과 반영(docs/architecture/fulfillment.md "발송 전 취소", "환불 결과 반영"). */
class ClaimUseCasesTest {

    static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");

    final FakeShipmentRepository shipments = new FakeShipmentRepository();
    final FakeClaimRepository claims = new FakeClaimRepository();
    final RecordingOutbox outbox = new RecordingOutbox();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    final RequestCancelClaimUseCase cancel =
            new RequestCancelClaimUseCase(shipments, claims, outbox, clock);
    final CompleteClaimUseCase complete =
            new CompleteClaimUseCase(claims, shipments, outbox, clock);
    final RecordRefundFailureUseCase recordFailure = new RecordRefundFailureUseCase(claims, clock);

    final UUID skuId = UUID.randomUUID();
    final Shipment shipment =
            shipments.save(
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
                                            List.of("화이트", "M"),
                                            UUID.randomUUID(),
                                            19_900,
                                            2,
                                            39_800)),
                            NOW,
                            NOW));

    RequestCancelClaimCommand cancelCommand() {
        return new RequestCancelClaimCommand(shipment.buyerId(), shipment.sellerOrderId());
    }

    @Test
    @DisplayName("발송 전 취소는 배송을 취소하고 ClaimRequested, ClaimApproved(전액)를 함께 낸다. 다시 해도 한 번이다")
    void cancels() {
        shipment.accept(NOW);

        cancel.execute(cancelCommand());
        cancel.execute(cancelCommand());

        assertThat(shipment.status()).isEqualTo(ShipmentStatus.CANCELLED);
        assertThat(claims.claims).hasSize(1);
        Claim claim = claims.claims.getFirst();
        assertThat(claim.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(outbox.types()).containsExactly(ClaimRequested.TYPE, ClaimApproved.TYPE);
        assertThat(outbox.events)
                .allSatisfy(
                        event -> {
                            assertThat(event.aggregateType()).isEqualTo("fulfillment.claim");
                            assertThat(event.aggregateId())
                                    .isEqualTo(shipment.sellerOrderId().toString());
                        });
        ClaimRequested requested = (ClaimRequested) outbox.events.getFirst().payload();
        assertThat(requested.reason()).isEqualTo("BUYER_CANCEL");
        assertThat(requested.fault()).isEqualTo("BUYER");
        assertThat(requested.fileIds()).isEmpty();
        ClaimApproved approved = (ClaimApproved) outbox.events.get(1).payload();
        assertThat(approved.type()).isEqualTo("CANCEL");
        assertThat(approved.refundAmount()).isEqualTo(39_800);
        assertThat(approved.approvedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("발송 뒤 취소는 409, 남의 하위 주문은 404이고 클레임과 이벤트가 없다")
    void rejectsCancel() {
        shipment.accept(NOW);
        shipment.ship(TrackingInfo.of("CJ_LOGISTICS", "123456789012"), NOW);

        assertThatThrownBy(() -> cancel.execute(cancelCommand()))
                .isInstanceOf(ShipmentInvalidStateException.class);
        assertThatThrownBy(
                        () ->
                                cancel.execute(
                                        new RequestCancelClaimCommand(
                                                UUID.randomUUID(), shipment.sellerOrderId())))
                .isInstanceOfSatisfying(
                        NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo("FULFILLMENT_NOT_FOUND"));
        assertThat(claims.claims).isEmpty();
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("진행 중인 클레임이 있으면 409 FULFILLMENT_CLAIM_IN_PROGRESS다")
    void rejectsWhileClaimInProgress() {
        Shipment other =
                Shipment.ready(
                        UUID.randomUUID(),
                        shipment.sellerOrderId(),
                        shipment.sellerId(),
                        shipment.buyerId(),
                        39_800,
                        shipment.lines(),
                        NOW,
                        NOW);
        claims.save(Claim.cancel(other, NOW));
        shipments.save(
                Shipment.ready(
                        UUID.randomUUID(),
                        shipment.sellerOrderId(),
                        shipment.sellerId(),
                        shipment.buyerId(),
                        39_800,
                        shipment.lines(),
                        NOW,
                        NOW));

        assertThatThrownBy(() -> cancel.execute(cancelCommand()))
                .isInstanceOfSatisfying(
                        ClaimInProgressException.class,
                        e -> assertThat(e.getCode()).isEqualTo("FULFILLMENT_CLAIM_IN_PROGRESS"));
    }

    @Test
    @DisplayName("환불 완료면 클레임을 끝내고 ClaimCompleted(재입고 항목)를 낸다. 다시 와도 한 번이다")
    void completes() {
        cancel.execute(cancelCommand());
        UUID claimId = claims.claims.getFirst().id();
        outbox.events.clear();

        recordFailure.execute(new RecordRefundFailureCommand(claimId, "NOT_CANCELABLE_AMOUNT"));
        assertThat(claims.claims.getFirst().refundFailureCode()).isEqualTo("NOT_CANCELABLE_AMOUNT");
        complete.execute(new CompleteClaimCommand(claimId));
        complete.execute(new CompleteClaimCommand(claimId));

        Claim claim = claims.claims.getFirst();
        assertThat(claim.status()).isEqualTo(ClaimStatus.COMPLETED);
        assertThat(claim.refundFailureCode()).isNull();
        assertThat(outbox.types()).containsExactly(ClaimCompleted.TYPE);
        ClaimCompleted event = (ClaimCompleted) outbox.events.getFirst().payload();
        assertThat(event.refundAmount()).isEqualTo(39_800);
        assertThat(event.restockLines()).containsExactly(new ClaimCompleted.RestockLine(skuId, 2));
        assertThat(event.completedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("모르는 클레임의 환불 결과는 넘긴다")
    void unknownClaim() {
        complete.execute(new CompleteClaimCommand(UUID.randomUUID()));
        recordFailure.execute(new RecordRefundFailureCommand(UUID.randomUUID(), "X"));

        assertThat(outbox.events).isEmpty();
    }
}
