package com.openmarket.auth.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.EmailAddress;
import com.openmarket.auth.command.domain.Role;
import com.openmarket.common.core.error.NotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChangeSellerRoleUseCaseTest {

    final Fakes.Accounts accounts = new Fakes.Accounts();
    final ChangeSellerRoleUseCase useCase = new ChangeSellerRoleUseCase(accounts, Fakes.clock());
    final UUID accountId = UUID.randomUUID();

    @Test
    @DisplayName("승인·해제 이벤트면 SELLER를 붙이고 정지 이벤트면 뗀다. 다시 와도 결과가 같다")
    void changesRole() {
        accounts.save(
                Account.register(
                        accountId, EmailAddress.of("seller@example.com"), "{bcrypt}x", Fakes.NOW));

        useCase.execute(new ChangeSellerRoleCommand(accountId, true));
        useCase.execute(new ChangeSellerRoleCommand(accountId, true));
        assertThat(accounts.rows.get(accountId).roles()).contains(Role.SELLER);

        useCase.execute(new ChangeSellerRoleCommand(accountId, false));
        useCase.execute(new ChangeSellerRoleCommand(accountId, false));
        assertThat(accounts.rows.get(accountId).roles()).containsExactly(Role.BUYER);
    }

    @Test
    @DisplayName("계정이 없으면 실패해서 리스너가 다시 시도한다")
    void failsWithoutAccount() {
        assertThatThrownBy(() -> useCase.execute(new ChangeSellerRoleCommand(accountId, true)))
                .isInstanceOf(NotFoundException.class);
    }
}
