package com.openmarket.fulfillment.ui.web;

import com.openmarket.fulfillment.command.application.ApproveClaimCommand;
import com.openmarket.fulfillment.command.application.ApproveClaimUseCase;
import com.openmarket.fulfillment.command.application.RejectClaimCommand;
import com.openmarket.fulfillment.command.application.RejectClaimUseCase;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자의 반품·교환 승인과 거부(CLM-3, docs/architecture/fulfillment.md "판매자 승인과 거부"). 주체는 토큰의 {@code sub}(=
 * sellerId)다. 상태로 결과가 정해져 다시 보내도 같으므로 {@code Idempotency-Key}는 받지 않는다.
 */
@RestController
@RequestMapping("/v1/seller/claims/{claimId}")
public class SellerClaimCommandController {

    private final ApproveClaimUseCase approve;
    private final RejectClaimUseCase reject;

    public SellerClaimCommandController(ApproveClaimUseCase approve, RejectClaimUseCase reject) {
        this.approve = approve;
        this.reject = reject;
    }

    /**
     * 승인. 이미 승인했으면 그대로 204. 반품인데 재입고 여부가 없으면 400 {@code FULFILLMENT_INVALID_INPUT}, 거부·완료된 클레임이면
     * 409 {@code FULFILLMENT_CLAIM_INVALID_STATE}, 다른 판매자의 것이면 404 {@code
     * FULFILLMENT_CLAIM_NOT_FOUND}.
     */
    @PostMapping("/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(
            Principal principal,
            @PathVariable UUID claimId,
            @Valid @RequestBody ApproveClaimRequest request) {
        approve.execute(new ApproveClaimCommand(sellerId(principal), claimId, request.restock()));
    }

    /** 거부. 이미 거부했으면 그대로 204. 사유가 1~500자가 아니면 400, 승인한 클레임이면 409. */
    @PostMapping("/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(
            Principal principal,
            @PathVariable UUID claimId,
            @Valid @RequestBody RejectClaimRequest request) {
        reject.execute(new RejectClaimCommand(sellerId(principal), claimId, request.reason()));
    }

    private static UUID sellerId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
