package com.openmarket.auth.command.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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

    @Test
    @DisplayName("가입한 계정은 구매자 역할만 갖고 이메일 인증 전이다")
    void registeredAccountIsUnverifiedBuyer() {
        Account account = register();

        assertThat(account.roles()).containsExactly(Role.BUYER);
        assertThat(account.isEmailVerified()).isFalse();
        assertThat(account.isActive()).isTrue();
    }

    @Test
    @DisplayName("이메일 인증은 처음 한 번만 상태를 바꾼다")
    void verifyEmailIsIdempotent() {
        Account account = register();

        assertThat(account.verifyEmail(NOW)).isTrue();
        assertThat(account.verifyEmail(NOW.plusSeconds(60))).isFalse();
        assertThat(account.isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("로그인을 5회 연속 실패하면 15분 동안 잠긴다")
    void locksAfterFiveFailures() {
        Account account = register();

        for (int i = 0; i < 4; i++) {
            account.recordLoginFailure(NOW);
        }
        assertThat(account.isLocked(NOW)).isFalse();

        account.recordLoginFailure(NOW);

        assertThat(account.isLocked(NOW)).isTrue();
        assertThat(account.isLocked(NOW.plus(Duration.ofMinutes(15)).minusSeconds(1))).isTrue();
        assertThat(account.isLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    @DisplayName("이번 실패로 잠갔을 때만 true다. 잠긴 동안의 실패는 false다")
    void reportsWhenFailureLocks() {
        Account account = register();
        for (int i = 0; i < 4; i++) {
            assertThat(account.recordLoginFailure(NOW)).isFalse();
        }

        assertThat(account.recordLoginFailure(NOW)).isTrue();
        assertThat(account.recordLoginFailure(NOW.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("잠금이 풀리면 실패 횟수는 처음부터 다시 센다")
    void countRestartsAfterLockExpires() {
        Account account = register();
        for (int i = 0; i < 5; i++) {
            account.recordLoginFailure(NOW);
        }
        Instant later = NOW.plus(Duration.ofMinutes(16));

        account.recordLoginFailure(later);

        assertThat(account.isLocked(later)).isFalse();
    }

    @Test
    @DisplayName("잠긴 동안의 실패는 잠금을 늘리지 않는다")
    void failuresWhileLockedDoNotExtend() {
        Account account = register();
        for (int i = 0; i < 5; i++) {
            account.recordLoginFailure(NOW);
        }

        account.recordLoginFailure(NOW.plus(Duration.ofMinutes(10)));

        assertThat(account.isLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    @DisplayName("로그인에 성공하면 실패 횟수를 지운다")
    void successResetsFailures() {
        Account account = register();
        for (int i = 0; i < 4; i++) {
            account.recordLoginFailure(NOW);
        }

        account.recordLoginSuccess();
        account.recordLoginFailure(NOW);

        assertThat(account.isLocked(NOW)).isFalse();
    }

    @Test
    @DisplayName("판매자 역할을 붙이고 뗀다. 이미 그 상태면 바뀌지 않는다")
    void grantsAndRevokesSellerRole() {
        Account account = register();

        assertThat(account.grantRole(Role.SELLER, NOW)).isTrue();
        assertThat(account.grantRole(Role.SELLER, NOW)).isFalse();
        assertThat(account.roles()).containsExactlyInAnyOrder(Role.BUYER, Role.SELLER);

        assertThat(account.revokeRole(Role.SELLER, NOW)).isTrue();
        assertThat(account.revokeRole(Role.SELLER, NOW)).isFalse();
        assertThat(account.roles()).containsExactly(Role.BUYER);
    }

    @Test
    @DisplayName("비활성화하면 로그인할 수 없고 이메일과 비밀번호 해시를 가명 값으로 바꾼다. 다시 해도 그대로다")
    void deactivates() {
        Account account = register();

        assertThat(account.deactivate("{bcrypt}random", NOW)).isTrue();
        assertThat(account.deactivate("{bcrypt}other", NOW)).isFalse();

        assertThat(account.isActive()).isFalse();
        assertThat(account.email().value())
                .isEqualTo("withdrawn-" + account.id() + "@withdrawn.invalid");
        assertThat(account.passwordHash()).isEqualTo("{bcrypt}random");
    }

    private static Account register() {
        return Account.register(
                UUID.randomUUID(), EmailAddress.of("buyer@example.com"), "{bcrypt}x", NOW);
    }
}
