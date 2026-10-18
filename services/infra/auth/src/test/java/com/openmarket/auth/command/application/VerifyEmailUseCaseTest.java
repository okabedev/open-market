package com.openmarket.auth.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountEmailVerified;
import com.openmarket.auth.command.domain.EmailAddress;
import com.openmarket.auth.command.domain.EmailVerification;
import com.openmarket.auth.command.domain.VerificationExpiredException;
import com.openmarket.auth.command.domain.VerificationInvalidException;
import com.openmarket.auth.command.domain.VerificationToken;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerifyEmailUseCaseTest {

    private final Fakes.Accounts accounts = new Fakes.Accounts();
    private final Fakes.Verifications verifications = new Fakes.Verifications();
    private final Fakes.RecordingOutbox outbox = new Fakes.RecordingOutbox();

    private VerifyEmailUseCase useCase(Clock clock) {
        return new VerifyEmailUseCase(verifications, accounts, outbox, clock);
    }

    private VerificationToken issue() {
        Account account =
                accounts.save(
                        Account.register(
                                UUID.randomUUID(),
                                EmailAddress.of("buyer@example.com"),
                                "{h}",
                                Fakes.NOW));
        VerificationToken token = VerificationToken.generate();
        verifications.save(
                EmailVerification.issue(UUID.randomUUID(), account.id(), token, Fakes.NOW));
        return token;
    }

    @Test
    @DisplayName("유효한 토큰이면 계정을 인증하고 AccountEmailVerified를 한 번 쌓는다. 같은 링크를 다시 눌러도 이벤트는 늘지 않는다")
    void verifiesOnce() {
        VerificationToken token = issue();

        useCase(Fakes.clock()).execute(new VerifyEmailCommand(token.raw()));
        useCase(Fakes.clock()).execute(new VerifyEmailCommand(token.raw()));

        assertThat(accounts.rows.values())
                .singleElement()
                .satisfies(a -> assertThat(a.isEmailVerified()).isTrue());
        assertThat(outbox.types()).containsExactly(AccountEmailVerified.TYPE);
    }

    @Test
    @DisplayName("없는 토큰은 거절한다")
    void rejectsUnknownToken() {
        issue();

        assertThatThrownBy(() -> useCase(Fakes.clock()).execute(new VerifyEmailCommand("unknown")))
                .isInstanceOf(VerificationInvalidException.class);
    }

    @Test
    @DisplayName("만료된 토큰은 거절하고 계정을 바꾸지 않는다")
    void rejectsExpiredToken() {
        VerificationToken token = issue();
        Clock later = Clock.fixed(Fakes.NOW.plus(Duration.ofHours(25)), ZoneOffset.UTC);

        assertThatThrownBy(() -> useCase(later).execute(new VerifyEmailCommand(token.raw())))
                .isInstanceOf(VerificationExpiredException.class);
        assertThat(outbox.events).isEmpty();
    }
}
