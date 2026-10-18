package com.openmarket.auth.command.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountTest {

    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");

    @Test
    @DisplayName("관리자 시드 계정은 ADMIN 역할만 갖고 이메일 인증을 마친 상태다")
    void seedAdminHasOnlyAdminRoleAndIsVerified() {
        Account account =
                Account.seedAdmin(
                        UUID.randomUUID(),
                        EmailAddress.of("admin@openmarket.local"),
                        "{bcrypt}x",
                        NOW);

        assertThat(account.roles()).containsExactly(Role.ADMIN);
        assertThat(account.isEmailVerified()).isTrue();
        assertThat(account.isActive()).isTrue();
        assertThat(account.createdAt()).isEqualTo(NOW);
    }
}
