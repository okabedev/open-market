package com.openmarket.fulfillment.ui.web;

import com.openmarket.fulfillment.command.application.RequestMediationCommand;
import com.openmarket.fulfillment.command.application.RequestMediationUseCase;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구매자의 클레임 중재 요청(CLM-3, docs/architecture/fulfillment.md "중재"). 주체는 토큰의 {@code sub}(= buyerId)다. 다시
 * 보내도 결과가 같아 {@code Idempotency-Key}는 받지 않는다.
 */
@RestController
@RequestMapping("/v1/claims/{claimId}")
public class BuyerClaimCommandController {

    private final RequestMediationUseCase requestMediation;

    public BuyerClaimCommandController(RequestMediationUseCase requestMediation) {
        this.requestMediation = requestMediation;
    }

    /**
     * 중재 요청. 이미 중재 중이면 그대로 204. 기한이 지났으면 409 {@code FULFILLMENT_MEDIATION_CLOSED}, 거부된 클레임이 아니거나 이미
     * 중재를 거쳤으면 409 {@code FULFILLMENT_CLAIM_INVALID_STATE}, 남의 클레임이면 404 {@code
     * FULFILLMENT_CLAIM_NOT_FOUND}.
     */
    @PostMapping("/mediation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestMediation(Principal principal, @PathVariable UUID claimId) {
        requestMediation.execute(
                new RequestMediationCommand(UUID.fromString(principal.getName()), claimId));
    }
}
