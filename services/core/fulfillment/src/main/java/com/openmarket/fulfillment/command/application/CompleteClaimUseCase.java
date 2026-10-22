package com.openmarket.fulfillment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.fulfillment.command.domain.Claim;
import com.openmarket.fulfillment.command.domain.ClaimCompleted;
import com.openmarket.fulfillment.command.domain.ClaimRepository;
import com.openmarket.fulfillment.command.domain.Shipment;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불이 끝난 클레임을 {@code COMPLETED}로 바꾸고 {@code ClaimCompleted}(재입고 항목)를
 * 낸다(docs/architecture/fulfillment.md "환불 결과 반영"). 클레임 행을 잠그고 바꾸며, 이미 끝났으면 넘긴다. 모르는 클레임은 경고 로그만
 * 남긴다.
 */
@Service
public class CompleteClaimUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompleteClaimUseCase.class);

    private final ClaimRepository claims;
    private final ShipmentRepository shipments;
    private final Outbox outbox;
    private final Clock clock;

    public CompleteClaimUseCase(
            ClaimRepository claims, ShipmentRepository shipments, Outbox outbox, Clock clock) {
        this.claims = claims;
        this.shipments = shipments;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(CompleteClaimCommand command) {
        Optional<Claim> found = claims.findForUpdateById(command.claimId());
        if (found.isEmpty()) {
            log.warn("모르는 클레임의 환불 완료라 넘긴다: claimId={}", command.claimId());
            return;
        }
        Claim claim = found.get();
        if (!claim.complete(clock.instant())) {
            return;
        }
        Shipment shipment =
                shipments
                        .findById(claim.shipmentId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "클레임의 배송이 없다: claimId=" + claim.id()));
        claims.save(claim);
        outbox.append(
                RequestCancelClaimUseCase.TOPIC,
                claim.sellerOrderId().toString(),
                ClaimCompleted.TYPE,
                ClaimCompleted.of(claim, shipment));
    }
}
