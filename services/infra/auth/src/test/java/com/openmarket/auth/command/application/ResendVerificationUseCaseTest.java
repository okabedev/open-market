package com.openmarket.auth.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.EmailAddress;
import com.openmarket.auth.command.domain.EmailVerificationRequested;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResendVerificationUseCaseTest {

    private final Fakes.Accounts accounts = new Fakes.Accounts();
    private final Fakes.Verifications verifications = new Fakes.Verifications();
    private final Fakes.RecordingOutbox outbox = new Fakes.RecordingOutbox();
    private final ResendVerificationUseCase useCase =
            new ResendVerificationUseCase(
                    accounts, verifications, Fakes.LINK, outbox, Fakes.clock());

    @Test
    @DisplayName("인증 전 계정이면 새 토큰을 만들고 EmailVerificationRequested를 쌓는다")
    void resendsForUnverified() {
        accounts.save(
                Account.register(
                        UUID.randomUUID(), EmailAddress.of("buyer@example.com"), "{h}", Fakes.NOW));

        useCase.execute(new ResendVerificationCommand("buyer@example.com"));

        assertThat(verifications.rows).hasSize(1);
        assertThat(outbox.types()).containsExactly(EmailVerificationRequested.TYPE);
    }

    @Test
    @DisplayName("없는 이메일, 이미 인증한 계정, 형식이 틀린 이메일은 조용히 넘어간다(가입 여부를 알려 주지 않는다)")
    void ignoresOthers() {
        Account verified =
                Account.register(
                        UUID.randomUUID(), EmailAddress.of("done@example.com"), "{h}", Fakes.NOW);
        verified.verifyEmail(Fakes.NOW);
        accounts.save(verified);

        useCase.execute(new ResendVerificationCommand("nobody@example.com"));
        useCase.execute(new ResendVerificationCommand("done@example.com"));
        useCase.execute(new ResendVerificationCommand("not-an-email"));

        assertThat(verifications.rows).isEmpty();
        assertThat(outbox.events).isEmpty();
    }
}
