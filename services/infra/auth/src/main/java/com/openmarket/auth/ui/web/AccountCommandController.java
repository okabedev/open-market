package com.openmarket.auth.ui.web;

import com.openmarket.auth.command.application.RegisterAccountCommand;
import com.openmarket.auth.command.application.RegisterAccountUseCase;
import com.openmarket.auth.command.application.ResendVerificationCommand;
import com.openmarket.auth.command.application.ResendVerificationUseCase;
import com.openmarket.auth.command.application.VerifyEmailCommand;
import com.openmarket.auth.command.application.VerifyEmailUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입과 이메일 인증(ACC-1). 모두 공개 경로다. gateway가 {@code /api/auth}를 떼고 넘긴다. 로그인은 API가 아니라 auth의 로그인 화면에서
 * 한다.
 */
@RestController
@RequestMapping("/v1/accounts")
public class AccountCommandController {

    private final RegisterAccountUseCase registerAccount;
    private final VerifyEmailUseCase verifyEmail;
    private final ResendVerificationUseCase resendVerification;

    public AccountCommandController(
            RegisterAccountUseCase registerAccount,
            VerifyEmailUseCase verifyEmail,
            ResendVerificationUseCase resendVerification) {
        this.registerAccount = registerAccount;
        this.verifyEmail = verifyEmail;
        this.resendVerification = resendVerification;
    }

    /** 가입. 인증 메일이 나간다. 같은 이메일이면 409 {@code AUTH_EMAIL_TAKEN}. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterAccountResponse register(@Valid @RequestBody RegisterAccountRequest request) {
        return new RegisterAccountResponse(
                registerAccount.execute(
                        new RegisterAccountCommand(
                                request.email(), request.password(), request.name())));
    }

    /** 인증 링크의 토큰으로 이메일을 인증한다. 같은 토큰을 다시 보내도 204다. */
    @PostMapping("/email-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody VerifyEmailRequest request) {
        verifyEmail.execute(new VerifyEmailCommand(request.token()));
    }

    /** 인증 메일을 다시 보낸다. 가입 여부를 알려 주지 않도록 항상 202다. */
    @PostMapping("/email-verification/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resend(@Valid @RequestBody ResendVerificationRequest request) {
        resendVerification.execute(new ResendVerificationCommand(request.email()));
    }
}
