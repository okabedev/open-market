package com.openmarket.member.ui.web;

import static com.openmarket.member.ui.web.MemberQueryController.memberId;

import com.openmarket.member.command.application.ApproveSellerApplicationUseCase;
import com.openmarket.member.command.application.ChangeSellerStatusCommand;
import com.openmarket.member.command.application.ReinstateSellerUseCase;
import com.openmarket.member.command.application.RejectSellerApplicationUseCase;
import com.openmarket.member.command.application.ReviewSellerApplicationCommand;
import com.openmarket.member.command.application.SuspendSellerUseCase;
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
 * 관리자 입점 심사와 판매자 정지(ADM-1, SEL-2, SEL-3). {@code ADMIN} 역할이 있어야 한다. 작업마다 감사 로그가 남는다(NFR-8).
 *
 * <p>같은 결과로 다시 부르면 아무것도 바꾸지 않고 204다. 이미 다른 결과로 심사한 신청이면 409 {@code
 * MEMBER_APPLICATION_ALREADY_REVIEWED}.
 */
@RestController
@RequestMapping("/v1/admin")
public class SellerReviewCommandController {

    private final ApproveSellerApplicationUseCase approve;
    private final RejectSellerApplicationUseCase reject;
    private final SuspendSellerUseCase suspend;
    private final ReinstateSellerUseCase reinstate;

    public SellerReviewCommandController(
            ApproveSellerApplicationUseCase approve,
            RejectSellerApplicationUseCase reject,
            SuspendSellerUseCase suspend,
            ReinstateSellerUseCase reinstate) {
        this.approve = approve;
        this.reject = reject;
        this.suspend = suspend;
        this.reinstate = reinstate;
    }

    /** 승인하면 판매자가 되고, 다음 토큰 갱신부터 판매자 앱에 들어갈 수 있다. */
    @PostMapping("/seller-applications/{applicationId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(Principal principal, @PathVariable UUID applicationId) {
        approve.execute(
                new ReviewSellerApplicationCommand(memberId(principal), applicationId, null));
    }

    /** 반려 사유가 꼭 있어야 한다. */
    @PostMapping("/seller-applications/{applicationId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(
            Principal principal,
            @PathVariable UUID applicationId,
            @Valid @RequestBody ReasonRequest request) {
        reject.execute(
                new ReviewSellerApplicationCommand(
                        memberId(principal), applicationId, request.reason()));
    }

    /** 정지 사유가 꼭 있어야 한다. 판매자 역할은 다음 토큰 갱신부터 빠진다. */
    @PostMapping("/sellers/{sellerId}/suspend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void suspend(
            Principal principal,
            @PathVariable UUID sellerId,
            @Valid @RequestBody ReasonRequest request) {
        suspend.execute(
                new ChangeSellerStatusCommand(memberId(principal), sellerId, request.reason()));
    }

    @PostMapping("/sellers/{sellerId}/reinstate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reinstate(Principal principal, @PathVariable UUID sellerId) {
        reinstate.execute(new ChangeSellerStatusCommand(memberId(principal), sellerId, null));
    }
}
