package com.openmarket.auth.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.EmailAddress;
import com.openmarket.common.core.error.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeactivateAccountUseCaseTest {

    final Fakes.Accounts accounts = new Fakes.Accounts();
    final List<UUID> revoked = new ArrayList<>();
    final DeactivateAccountUseCase useCase =
            new DeactivateAccountUseCase(
                    accounts, raw -> "{hashed}" + raw.length(), revoked::add, Fakes.clock());
    final UUID accountId = UUID.randomUUID();

    @Test
    @DisplayName("계정을 비활성화하고 인가·리프레시 토큰을 지운다. 같은 이메일로 다시 가입할 수 있게 이메일을 비운다")
    void deactivates() {
        accounts.save(
                Account.register(
                        accountId, EmailAddress.of("leaving@example.com"), "{bcrypt}x", Fakes.NOW));

        useCase.execute(new DeactivateAccountCommand(accountId));
        useCase.execute(new DeactivateAccountCommand(accountId));

        Account account = accounts.rows.get(accountId);
        assertThat(account.isActive()).isFalse();
        assertThat(account.passwordHash()).startsWith("{hashed}");
        assertThat(accounts.findByEmail(EmailAddress.of("leaving@example.com"))).isEmpty();
        assertThat(revoked).containsExactly(accountId, accountId);
    }

    @Test
    @DisplayName("계정이 없으면 실패해서 리스너가 다시 시도한다")
    void failsWithoutAccount() {
        assertThatThrownBy(() -> useCase.execute(new DeactivateAccountCommand(accountId)))
                .isInstanceOf(NotFoundException.class);
    }
}
