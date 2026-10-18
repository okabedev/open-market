package com.openmarket.member.ui.web;

import static com.openmarket.member.ui.web.MemberQueryController.memberId;

import com.openmarket.common.web.idempotency.Idempotent;
import com.openmarket.member.command.application.SubmitSellerApplicationCommand;
import com.openmarket.member.command.application.SubmitSellerApplicationUseCase;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 입점 신청(SEL-1). */
@RestController
public class SellerApplicationCommandController {

    private final SubmitSellerApplicationUseCase submit;

    public SellerApplicationCommandController(SubmitSellerApplicationUseCase submit) {
        this.submit = submit;
    }

    /**
     * 입점을 신청한다. {@code Idempotency-Key}가 필요하다. 이메일 인증 전이면 422 {@code MEMBER_EMAIL_NOT_VERIFIED}, 심사
     * 중인 신청이 있으면 409 {@code MEMBER_APPLICATION_PENDING}, 이미 판매자면 409 {@code MEMBER_ALREADY_SELLER}.
     */
    @PostMapping("/v1/members/me/seller-applications")
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotent
    public SubmitSellerApplicationResponse submit(
            Principal principal, @Valid @RequestBody SubmitSellerApplicationRequest request) {
        return new SubmitSellerApplicationResponse(
                submit.execute(
                        new SubmitSellerApplicationCommand(
                                memberId(principal),
                                request.storeName(),
                                request.businessNumber(),
                                request.representativeName(),
                                request.bankName(),
                                request.accountHolder(),
                                request.accountNumber())));
    }
}
