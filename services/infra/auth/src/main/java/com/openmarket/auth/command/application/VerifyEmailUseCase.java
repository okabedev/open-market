package com.openmarket.auth.command.application;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountEmailVerified;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.command.domain.EmailVerification;
import com.openmarket.auth.command.domain.EmailVerificationRepository;
import com.openmarket.auth.command.domain.VerificationInvalidException;
import com.openmarket.auth.command.domain.VerificationToken;
import com.openmarket.common.messaging.outbox.Outbox;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이메일을 인증한다(ACC-1). 처음 인증할 때만 {@code AccountEmailVerified}를 쌓는다. 같은 링크를 다시 눌러도 결과가 같다(멱등). */
@Service
public class VerifyEmailUseCase {

    private final EmailVerificationRepository verifications;
    private final AccountRepository accounts;
    private final Outbox outbox;
    private final Clock clock;

    public VerifyEmailUseCase(
            EmailVerificationRepository verifications,
            AccountRepository accounts,
            Outbox outbox,
            Clock clock) {
        this.verifications = verifications;
        this.accounts = accounts;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(VerifyEmailCommand command) {
        EmailVerification verification =
                verifications
                        .findByTokenHash(VerificationToken.hash(command.token()))
                        .orElseThrow(VerificationInvalidException::new);
        Instant now = clock.instant();
        Account account =
                accounts.findById(verification.use(now))
                        .orElseThrow(VerificationInvalidException::new);
        if (account.verifyEmail(now)) {
            outbox.append(
                    RegisterAccountUseCase.TOPIC,
                    account.id().toString(),
                    AccountEmailVerified.TYPE,
                    new AccountEmailVerified(account.id()));
        }
    }
}
