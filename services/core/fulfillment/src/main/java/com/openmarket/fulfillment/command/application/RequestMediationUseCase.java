package com.openmarket.fulfillment.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.fulfillment.command.domain.Claim;
import com.openmarket.fulfillment.command.domain.ClaimInProgressException;
import com.openmarket.fulfillment.command.domain.ClaimMediationRequested;
import com.openmarket.fulfillment.command.domain.ClaimNotFoundException;
import com.openmarket.fulfillment.command.domain.ClaimRepository;
import com.openmarket.fulfillment.command.domain.ClaimType;
import com.openmarket.fulfillment.command.domain.PurchaseConfirmationRepository;
import com.openmarket.fulfillment.command.domain.PurchaseConfirmedException;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매자 중재 요청(docs/architecture/fulfillment.md "중재"). 클레임과 하위 주문의 마지막 배송을 잠가 구매확정·새 클레임 신청과 겹치지 않게
 * 하고, {@code MEDIATING}으로 바꿔 {@code ClaimMediationRequested}를 낸다. 이미 중재 중이면 아무것도 하지 않는다.
 */
@Service
public class RequestMediationUseCase {

    private final ClaimRepository claims;
    private final ShipmentRepository shipments;
    private final PurchaseConfirmationRepository confirmations;
    private final Outbox outbox;
    private final Clock clock;
    private final Duration mediationWindow;

    public RequestMediationUseCase(
            ClaimRepository claims,
            ShipmentRepository shipments,
            PurchaseConfirmationRepository confirmations,
            Outbox outbox,
            Clock clock,
            @Value("${openmarket.fulfillment.claim.mediation-window:7d}")
                    Duration mediationWindow) {
        this.claims = claims;
        this.shipments = shipments;
        this.confirmations = confirmations;
        this.outbox = outbox;
        this.clock = clock;
        this.mediationWindow = mediationWindow;
    }

    @Transactional
    public void execute(RequestMediationCommand command) {
        Claim claim =
                claims.findForUpdateById(command.claimId())
                        .filter(found -> found.buyerId().equals(command.buyerId()))
                        .filter(found -> found.type() != ClaimType.CANCEL)
                        .orElseThrow(ClaimNotFoundException::new);
        // 구매확정·새 클레임 신청과 같은 배송 행을 잠근다. 결과는 쓰지 않는다.
        shipments.findLatestForUpdateBySellerOrderId(claim.sellerOrderId());
        if (confirmations.existsBySellerOrderId(claim.sellerOrderId())) {
            throw new PurchaseConfirmedException();
        }
        if (!claim.isOngoing() && claims.existsOngoingBySellerOrderId(claim.sellerOrderId())) {
            throw new ClaimInProgressException();
        }
        if (!claim.requestMediation(clock.instant(), mediationWindow)) {
            return;
        }
        claims.save(claim);
        outbox.append(
                RequestCancelClaimUseCase.TOPIC,
                claim.sellerOrderId().toString(),
                ClaimMediationRequested.TYPE,
                ClaimMediationRequested.of(claim));
    }
}
