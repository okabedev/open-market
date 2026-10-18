package com.openmarket.auth.command.application;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRegistered;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.command.domain.EmailAddress;
import com.openmarket.auth.command.domain.EmailAlreadyRegisteredException;
import com.openmarket.auth.command.domain.EmailVerification;
import com.openmarket.auth.command.domain.EmailVerificationRepository;
import com.openmarket.auth.command.domain.EmailVerificationRequested;
import com.openmarket.auth.command.domain.PasswordPolicy;
import com.openmarket.auth.command.domain.VerificationToken;
import com.openmarket.common.core.id.Ids;
import com.openmarket.common.messaging.outbox.Outbox;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일로 가입한다(ACC-1). 계정과 인증 토큰을 만들고 {@code AccountRegistered}, {@code EmailVerificationRequested}를
 * 쌓는다.
 *
 * <p>같은 이메일이 동시에 가입하면 이메일 유니크 제약으로 한쪽 커밋이 실패한다.
 */
@Service
public class RegisterAccountUseCase {

    static final String TOPIC = "auth.account";

    private final AccountRepository accounts;
    private final EmailVerificationRepository verifications;
    private final PasswordHasherPort passwordHasher;
    private final VerificationLinkPort verificationLink;
    private final Outbox outbox;
    private final Clock clock;

    public RegisterAccountUseCase(
            AccountRepository accounts,
            EmailVerificationRepository verifications,
            PasswordHasherPort passwordHasher,
            VerificationLinkPort verificationLink,
            Outbox outbox,
            Clock clock) {
        this.accounts = accounts;
        this.verifications = verifications;
        this.passwordHasher = passwordHasher;
        this.verificationLink = verificationLink;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** 새 계정 ID(= memberId)를 돌려준다. */
    @Transactional
    public UUID execute(RegisterAccountCommand command) {
        EmailAddress email = EmailAddress.of(command.email());
        PasswordPolicy.check(command.password());
        if (accounts.findByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }
        Instant now = clock.instant();
        Account account =
                accounts.save(
                        Account.register(
                                Ids.newId(), email, passwordHasher.hash(command.password()), now));
        String key = account.id().toString();
        outbox.append(
                TOPIC,
                key,
                AccountRegistered.TYPE,
                new AccountRegistered(account.id(), email.value(), command.name().strip()));
        VerificationToken token = VerificationToken.generate();
        EmailVerification verification =
                verifications.save(EmailVerification.issue(Ids.newId(), account.id(), token, now));
        outbox.append(
                TOPIC,
                key,
                EmailVerificationRequested.TYPE,
                new EmailVerificationRequested(
                        account.id(),
                        email.value(),
                        verificationLink.link(token.raw()),
                        verification.expiresAt()));
        return account.id();
    }
}
