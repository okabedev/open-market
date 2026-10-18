package com.openmarket.auth.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRegistered;
import com.openmarket.auth.command.domain.EmailAlreadyRegisteredException;
import com.openmarket.auth.command.domain.EmailVerificationRequested;
import com.openmarket.auth.command.domain.InvalidPasswordException;
import com.openmarket.auth.command.domain.VerificationToken;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegisterAccountUseCaseTest {

    private final Fakes.Accounts accounts = new Fakes.Accounts();
    private final Fakes.Verifications verifications = new Fakes.Verifications();
    private final Fakes.RecordingOutbox outbox = new Fakes.RecordingOutbox();
    private final RegisterAccountUseCase useCase =
            new RegisterAccountUseCase(
                    accounts, verifications, Fakes.HASHER, Fakes.LINK, outbox, Fakes.clock());

    @Test
    @DisplayName("계정과 인증 토큰을 만들고 AccountRegistered, EmailVerificationRequested를 outbox에 쌓는다")
    void registers() {
        UUID accountId =
                useCase.execute(
                        new RegisterAccountCommand("Buyer@Example.com", "password123", "홍길동"));

        Account account = accounts.rows.get(accountId);
        assertThat(account.email().value()).isEqualTo("buyer@example.com");
        assertThat(account.passwordHash()).isEqualTo("{hashed}password123");
        assertThat(account.isEmailVerified()).isFalse();

        assertThat(outbox.types())
                .containsExactly(AccountRegistered.TYPE, EmailVerificationRequested.TYPE);
        assertThat(outbox.events)
                .allSatisfy(
                        e -> {
                            assertThat(e.aggregateType()).isEqualTo("auth.account");
                            assertThat(e.aggregateId()).isEqualTo(accountId.toString());
                        });
        assertThat(outbox.events.get(0).payload())
                .isEqualTo(new AccountRegistered(accountId, "buyer@example.com", "홍길동"));

        EmailVerificationRequested requested =
                (EmailVerificationRequested) outbox.events.get(1).payload();
        String rawToken =
                requested
                        .verificationUrl()
                        .substring(requested.verificationUrl().indexOf("token=") + 6);
        assertThat(verifications.rows)
                .singleElement()
                .satisfies(
                        v -> {
                            assertThat(v.accountId()).isEqualTo(accountId);
                            assertThat(v.tokenHash()).isEqualTo(VerificationToken.hash(rawToken));
                        });
        assertThat(requested.expiresAt()).isEqualTo(verifications.rows.getFirst().expiresAt());
    }

    @Test
    @DisplayName("같은 이메일(대소문자 무시)로는 다시 가입할 수 없다")
    void rejectsDuplicateEmail() {
        useCase.execute(new RegisterAccountCommand("buyer@example.com", "password123", "홍길동"));

        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        new RegisterAccountCommand(
                                                "BUYER@example.com", "password456", "김철수")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(accounts.rows).hasSize(1);
    }

    @Test
    @DisplayName("비밀번호 규칙에 맞지 않으면 아무것도 저장하지 않는다")
    void rejectsWeakPassword() {
        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        new RegisterAccountCommand(
                                                "buyer@example.com", "short1", "홍길동")))
                .isInstanceOf(InvalidPasswordException.class);
        assertThat(accounts.rows).isEmpty();
        assertThat(outbox.events).isEmpty();
    }
}
