package com.openmarket.auth.command.application;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.command.domain.EmailAddress;
import com.openmarket.auth.command.domain.EmailVerification;
import com.openmarket.auth.command.domain.EmailVerificationRepository;
import com.openmarket.auth.command.domain.EmailVerificationRequested;
import com.openmarket.auth.command.domain.InvalidEmailException;
import com.openmarket.auth.command.domain.VerificationToken;
import com.openmarket.common.core.id.Ids;
import com.openmarket.common.messaging.outbox.Outbox;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 메일을 다시 보낸다. 인증 전의 활성 계정에만 새 토큰을 만든다. 없는 이메일, 이미 인증한 계정은 조용히 넘어가서 응답으로 가입 여부를 알 수 없게 한다. 이전 토큰은
 * 만료될 때까지 그대로 쓸 수 있다. 남용은 레이트 리밋으로 막는다.
 */
@Service
public class ResendVerificationUseCase {

    private final AccountRepository accounts;
    private final EmailVerificationRepository verifications;
    private final VerificationLinkPort verificationLink;
    private final Outbox outbox;
    private final Clock clock;

    public ResendVerificationUseCase(
            AccountRepository accounts,
            EmailVerificationRepository verifications,
            VerificationLinkPort verificationLink,
            Outbox outbox,
            Clock clock) {
        this.accounts = accounts;
        this.verifications = verifications;
        this.verificationLink = verificationLink;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(ResendVerificationCommand command) {
        Optional<Account> found;
        try {
            found = accounts.findByEmail(EmailAddress.of(command.email()));
        } catch (InvalidEmailException e) {
            return;
        }
        if (found.isEmpty() || !found.get().isActive() || found.get().isEmailVerified()) {
            return;
        }
        Account account = found.get();
        VerificationToken token = VerificationToken.generate();
        EmailVerification verification =
                verifications.save(
                        EmailVerification.issue(Ids.newId(), account.id(), token, clock.instant()));
        outbox.append(
                RegisterAccountUseCase.TOPIC,
                account.id().toString(),
                EmailVerificationRequested.TYPE,
                new EmailVerificationRequested(
                        account.id(),
                        account.email().value(),
                        verificationLink.link(token.raw()),
                        verification.expiresAt()));
    }
}
