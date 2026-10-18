package com.openmarket.auth.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailVerificationTest {

    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    @Test
    @DisplayName("토큰은 원문과 해시가 다르고, 해시만 저장한다")
    void storesOnlyHash() {
        VerificationToken token = VerificationToken.generate();

        EmailVerification verification =
                EmailVerification.issue(UUID.randomUUID(), ACCOUNT_ID, token, NOW);

        assertThat(token.raw()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(verification.tokenHash()).isEqualTo(VerificationToken.hash(token.raw()));
        assertThat(verification.tokenHash()).isNotEqualTo(token.raw());
        assertThat(verification.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("만료 전에는 쓸 수 있고, 다시 써도 같은 결과다")
    void useIsIdempotent() {
        EmailVerification verification =
                EmailVerification.issue(
                        UUID.randomUUID(), ACCOUNT_ID, VerificationToken.generate(), NOW);

        assertThat(verification.use(NOW.plusSeconds(10))).isEqualTo(ACCOUNT_ID);
        assertThat(verification.use(NOW.plus(Duration.ofHours(30)))).isEqualTo(ACCOUNT_ID);
    }

    @Test
    @DisplayName("쓰지 않은 채 24시간이 지나면 만료다")
    void rejectsExpired() {
        EmailVerification verification =
                EmailVerification.issue(
                        UUID.randomUUID(), ACCOUNT_ID, VerificationToken.generate(), NOW);

        assertThatThrownBy(() -> verification.use(NOW.plus(Duration.ofHours(24))))
                .isInstanceOf(VerificationExpiredException.class);
    }
}
