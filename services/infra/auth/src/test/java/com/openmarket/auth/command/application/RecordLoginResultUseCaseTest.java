package com.openmarket.auth.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.EmailAddress;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link RecordLoginFailureUseCase}와 {@link RecordLoginSuccessUseCase}. */
class RecordLoginResultUseCaseTest {

    private final Fakes.Accounts accounts = new Fakes.Accounts();
    private final RecordLoginFailureUseCase failure =
            new RecordLoginFailureUseCase(accounts, Fakes.clock());
    private final RecordLoginSuccessUseCase success = new RecordLoginSuccessUseCase(accounts);

    @Test
    @DisplayName("같은 이메일로 5번 실패하면 잠기고, 성공하면 실패 횟수를 지운다")
    void recordsFailuresAndSuccess() {
        Account account =
                accounts.save(
                        Account.register(
                                UUID.randomUUID(),
                                EmailAddress.of("buyer@example.com"),
                                "{h}",
                                Fakes.NOW));

        for (int i = 0; i < 4; i++) {
            failure.execute(new RecordLoginFailureCommand("Buyer@example.com"));
        }
        success.execute(new RecordLoginSuccessCommand(account.id()));
        failure.execute(new RecordLoginFailureCommand("buyer@example.com"));
        assertThat(account.isLocked(Fakes.NOW)).isFalse();

        for (int i = 0; i < 3; i++) {
            assertThat(failure.execute(new RecordLoginFailureCommand("buyer@example.com")))
                    .isEqualTo(new RecordLoginFailureUseCase.Result(account.id(), false));
        }
        assertThat(failure.execute(new RecordLoginFailureCommand("buyer@example.com")))
                .isEqualTo(new RecordLoginFailureUseCase.Result(account.id(), true));
        assertThat(account.isLocked(Fakes.NOW)).isTrue();
    }

    @Test
    @DisplayName("없는 계정이나 형식이 틀린 이메일의 실패는 무시한다")
    void ignoresUnknown() {
        assertThat(failure.execute(new RecordLoginFailureCommand("nobody@example.com")))
                .isEqualTo(new RecordLoginFailureUseCase.Result(null, false));
        assertThat(failure.execute(new RecordLoginFailureCommand("not-an-email")))
                .isEqualTo(new RecordLoginFailureUseCase.Result(null, false));
        success.execute(new RecordLoginSuccessCommand(UUID.randomUUID()));

        assertThat(accounts.rows).isEmpty();
    }
}
