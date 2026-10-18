package com.openmarket.auth.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.command.domain.EmailAddress;
import com.openmarket.auth.command.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeedAdminUseCaseTest {

    private final FakeAccounts accounts = new FakeAccounts();
    private final SeedAdminUseCase useCase =
            new SeedAdminUseCase(
                    accounts,
                    raw -> "{hashed}" + raw,
                    Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC));

    @Test
    @DisplayName("관리자 계정이 없으면 만든다")
    void createsAdminWhenAbsent() {
        boolean created =
                useCase.execute(new SeedAdminCommand("Admin@OpenMarket.local", "admin-pass-1"));

        assertThat(created).isTrue();
        assertThat(accounts.saved).hasSize(1);
        Account admin = accounts.saved.getFirst();
        assertThat(admin.email().value()).isEqualTo("admin@openmarket.local");
        assertThat(admin.passwordHash()).isEqualTo("{hashed}admin-pass-1");
        assertThat(admin.roles()).containsExactly(Role.ADMIN);
    }

    @Test
    @DisplayName("같은 이메일 계정이 이미 있으면 다시 만들지 않는다")
    void isIdempotent() {
        useCase.execute(new SeedAdminCommand("admin@openmarket.local", "admin-pass-1"));

        boolean created =
                useCase.execute(new SeedAdminCommand("admin@openmarket.local", "other-pass-2"));

        assertThat(created).isFalse();
        assertThat(accounts.saved).hasSize(1);
        assertThat(accounts.saved.getFirst().passwordHash()).isEqualTo("{hashed}admin-pass-1");
    }

    static final class FakeAccounts implements AccountRepository {
        final List<Account> saved = new ArrayList<>();

        @Override
        public Optional<Account> findById(UUID id) {
            return saved.stream().filter(a -> a.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Account> findByEmail(EmailAddress email) {
            return saved.stream().filter(a -> a.email().equals(email)).findFirst();
        }

        @Override
        public Optional<Account> findForUpdateByEmail(EmailAddress email) {
            return findByEmail(email);
        }

        @Override
        public Account save(Account account) {
            saved.add(account);
            return account;
        }
    }
}
