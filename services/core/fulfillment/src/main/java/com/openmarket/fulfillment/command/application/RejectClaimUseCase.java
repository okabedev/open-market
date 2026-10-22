package com.openmarket.fulfillment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.fulfillment.command.domain.Claim;
import com.openmarket.fulfillment.command.domain.ClaimRejected;
import com.openmarket.fulfillment.command.domain.ClaimRepository;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자의 반품·교환 거부(CLM-3, docs/architecture/fulfillment.md "판매자 승인과 거부"). 클레임 행을 잠그고 거부해 {@code
 * ClaimRejected}를 내고, 구매자가 중재를 요청할 수 있게 자동 구매확정 기한을 늦춘다. 이미 거부했으면 아무것도 하지 않는다.
 */
@Service
public class RejectClaimUseCase {

    private final ClaimRepository claims;
    private final ShipmentRepository shipments;
    private final Outbox outbox;
    private final Clock clock;
    private final Duration mediationWindow;

    public RejectClaimUseCase(
            ClaimRepository claims,
            ShipmentRepository shipments,
            Outbox outbox,
            Clock clock,
            @Value("${openmarket.fulfillment.claim.mediation-window:7d}")
                    Duration mediationWindow) {
        this.claims = claims;
        this.shipments = shipments;
        this.outbox = outbox;
        this.clock = clock;
        this.mediationWindow = mediationWindow;
    }

    @Transactional
    public void execute(RejectClaimCommand command) {
        Claim claim =
                ApproveClaimUseCase.findSellerClaim(claims, command.sellerId(), command.claimId());
        Instant now = clock.instant();
        if (!claim.reject(command.reason(), now)) {
            return;
        }
        claims.save(claim);
        shipments
                .findLatestForUpdateBySellerOrderId(claim.sellerOrderId())
                .ifPresent(
                        latest -> {
                            latest.extendConfirmDue(now.plus(mediationWindow));
                            shipments.save(latest);
                        });
        outbox.append(
                RequestCancelClaimUseCase.TOPIC,
                claim.sellerOrderId().toString(),
                ClaimRejected.TYPE,
                ClaimRejected.bySeller(claim));
    }
}
