package com.openmarket.fulfillment.ui.web;

import com.openmarket.fulfillment.command.application.ApproveMediationCommand;
import com.openmarket.fulfillment.command.application.ApproveMediationUseCase;
import com.openmarket.fulfillment.command.application.RejectMediationCommand;
import com.openmarket.fulfillment.command.application.RejectMediationUseCase;
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
 * 관리자의 클레임 중재 결정(ADM-2, docs/architecture/fulfillment.md "중재"). 경로 접두어로 {@code ADMIN} 역할을 검사한다. 상태로
 * 결과가 정해져 다시 보내도 같으므로 {@code Idempotency-Key}는 받지 않는다.
 */
@RestController
@RequestMapping("/v1/admin/claims/{claimId}")
public class AdminClaimCommandController {

    private final ApproveMediationUseCase approve;
    private final RejectMediationUseCase reject;

    public AdminClaimCommandController(
            ApproveMediationUseCase approve, RejectMediationUseCase reject) {
        this.approve = approve;
        this.reject = reject;
    }

    /** 중재 승인. 이미 승인했으면 그대로 204. 중재 중이 아니면 409, 교환에 구매자 귀책이면 400. */
    @PostMapping("/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(
            Principal principal,
            @PathVariable UUID claimId,
            @Valid @RequestBody ApproveMediationRequest request) {
        approve.execute(new ApproveMediationCommand(adminId(principal), claimId, request.fault()));
    }

    /** 중재 거부 확정. 이미 확정했으면 그대로 204. 중재 중이 아니면 409, 사유가 1~500자가 아니면 400. */
    @PostMapping("/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(
            Principal principal,
            @PathVariable UUID claimId,
            @Valid @RequestBody RejectMediationRequest request) {
        reject.execute(new RejectMediationCommand(adminId(principal), claimId, request.reason()));
    }

    private static UUID adminId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
