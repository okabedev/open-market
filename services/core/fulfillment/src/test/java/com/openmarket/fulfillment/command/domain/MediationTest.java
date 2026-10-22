package com.openmarket.fulfillment.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 중재(docs/architecture/fulfillment.md "중재"). */
class MediationTest {

    static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");
    static final Duration WINDOW = Duration.ofDays(7);
    static final long FEE = 6_000;

    final UUID sku = UUID.randomUUID();
    final Shipment shipment = delivered();

    Shipment delivered() {
        Shipment shipment =
                Shipment.ready(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        39_800,
                        List.of(ClaimTest.line(sku, 2, 19_900)),
                        NOW,
                        NOW);
        shipment.accept(NOW);
        shipment.ship(TrackingInfo.of("CJ_LOGISTICS", "123456789012"), NOW);
        shipment.startTransit(NOW);
        shipment.deliver(NOW, Duration.ofDays(7));
        return shipment;
    }

    Claim rejected(ClaimType type, ClaimReason reason) {
        Claim claim = Claim.request(type, shipment, reason, "불량", List.of(), NOW);
        claim.reject("사용 흔적", NOW);
        return claim;
    }

    @Test
    @DisplayName("거부된 클레임은 기한 안에 한 번 중재를 요청할 수 있다. 다시 요청해도 그대로다")
    void requestsMediation() {
        Claim claim = rejected(ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND);

        assertThat(claim.requestMediation(NOW.plus(Duration.ofDays(6)), WINDOW)).isTrue();
        assertThat(claim.requestMediation(NOW.plus(Duration.ofDays(6)), WINDOW)).isFalse();

        assertThat(claim.status()).isEqualTo(ClaimStatus.MEDIATING);
        assertThat(claim.mediationRequestedAt()).isEqualTo(NOW.plus(Duration.ofDays(6)));
        assertThat(claim.isOngoing()).isTrue();
    }

    @Test
    @DisplayName(
            "기한이 지나면 409 FULFILLMENT_MEDIATION_CLOSED, 거부되지 않은 클레임은 409 FULFILLMENT_CLAIM_INVALID_STATE")
    void mediationRules() {
        Claim late = rejected(ClaimType.RETURN, ClaimReason.DEFECTIVE);
        assertThatThrownBy(() -> late.requestMediation(NOW.plus(WINDOW).plusSeconds(1), WINDOW))
                .isInstanceOfSatisfying(
                        MediationClosedException.class,
                        e -> assertThat(e.getCode()).isEqualTo("FULFILLMENT_MEDIATION_CLOSED"));

        Claim requested =
                Claim.request(
                        ClaimType.RETURN, shipment, ClaimReason.DEFECTIVE, "x", List.of(), NOW);
        assertThatThrownBy(() -> requested.requestMediation(NOW, WINDOW))
                .isInstanceOfSatisfying(
                        ClaimInvalidStateException.class,
                        e ->
                                assertThat(e.getProperties())
                                        .containsEntry("claimStatus", "REQUESTED"));
    }

    @Test
    @DisplayName("중재 승인은 관리자가 고른 귀책으로 환불 금액을 정하고 재입고하지 않는다. 다시 승인해도 그대로다")
    void approvesByMediation() {
        Claim claim = rejected(ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND);
        claim.requestMediation(NOW, WINDOW);

        assertThat(claim.approveByMediation(shipment, Fault.SELLER, FEE, NOW.plusSeconds(1)))
                .isEqualTo(Claim.Approval.APPROVED);
        assertThat(claim.approveByMediation(shipment, Fault.BUYER, FEE, NOW.plusSeconds(2)))
                .isEqualTo(Claim.Approval.UNCHANGED);

        assertThat(claim.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.fault()).isEqualTo(Fault.SELLER);
        assertThat(claim.refundAmount()).isEqualTo(39_800);
        assertThat(claim.restock()).isFalse();
        assertThat(claim.mediationDecidedAt()).isEqualTo(NOW.plusSeconds(1));

        claim.complete(NOW);
        assertThat(ClaimCompleted.of(claim, shipment).restockLines()).isEmpty();
    }

    @Test
    @DisplayName("중재로 승인하는 교환은 판매자 귀책이어야 한다")
    void exchangeNeedsSellerFault() {
        Claim claim = rejected(ClaimType.EXCHANGE, ClaimReason.DEFECTIVE);
        claim.requestMediation(NOW, WINDOW);

        assertThatThrownBy(() -> claim.approveByMediation(shipment, Fault.BUYER, FEE, NOW))
                .isInstanceOf(InvalidFulfillmentInputException.class);
        assertThat(claim.approveByMediation(shipment, Fault.SELLER, FEE, NOW))
                .isEqualTo(Claim.Approval.APPROVED);
        assertThat(claim.refundAmount()).isZero();
        assertThat(claim.reship(shipment, NOW).claimId()).isEqualTo(claim.id());
    }

    @Test
    @DisplayName("중재 거부 확정은 사유를 따로 남기고 다시 중재를 요청할 수 없다. 다시 확정해도 그대로다")
    void rejectsByMediation() {
        Claim claim = rejected(ClaimType.RETURN, ClaimReason.DEFECTIVE);
        claim.requestMediation(NOW, WINDOW);

        assertThat(claim.rejectByMediation(" 판매자 판단이 맞습니다 ", NOW.plusSeconds(5))).isTrue();
        assertThat(claim.rejectByMediation("다시", NOW.plusSeconds(6))).isFalse();

        assertThat(claim.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(claim.rejectionReason()).isEqualTo("사용 흔적");
        assertThat(claim.mediationReason()).isEqualTo("판매자 판단이 맞습니다");
        assertThat(claim.mediationDecidedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThatThrownBy(() -> claim.requestMediation(NOW, WINDOW))
                .isInstanceOf(ClaimInvalidStateException.class);
        assertThatThrownBy(() -> claim.approveByMediation(shipment, Fault.SELLER, FEE, NOW))
                .isInstanceOf(ClaimInvalidStateException.class);

        ClaimRejected event = ClaimRejected.byMediation(claim);
        assertThat(event.mediated()).isTrue();
        assertThat(event.reason()).isEqualTo("판매자 판단이 맞습니다");
        assertThat(event.rejectedAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    @DisplayName("중재 중이 아닌 클레임은 관리자가 결정할 수 없다")
    void decideOnlyWhileMediating() {
        Claim claim = rejected(ClaimType.RETURN, ClaimReason.DEFECTIVE);

        assertThatThrownBy(() -> claim.rejectByMediation("x", NOW))
                .isInstanceOf(ClaimInvalidStateException.class);
        assertThatThrownBy(() -> claim.approveByMediation(shipment, Fault.SELLER, FEE, NOW))
                .isInstanceOf(ClaimInvalidStateException.class);
    }
}
