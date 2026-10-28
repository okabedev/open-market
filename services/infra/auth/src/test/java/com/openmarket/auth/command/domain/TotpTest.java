package com.openmarket.auth.command.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpTest {

    /** RFC 6238 부록 B의 SHA-1 시험 비밀 값. */
    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest
    @CsvSource({
        "59, 287082",
        "1111111109, 081804",
        "1111111111, 050471",
        "1234567890, 005924",
        "2000000000, 279037"
    })
    @DisplayName("RFC 6238 시험 벡터(SHA-1, 30초)의 뒤 6자리와 같다")
    void matchesRfc6238Vectors(long epochSecond, String expected) {
        assertThat(Totp.code(SECRET, Totp.step(Instant.ofEpochSecond(epochSecond))))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("앞뒤 한 단계까지 받고 맞은 단계를 돌려준다. 두 단계 떨어지면 받지 않는다")
    void acceptsOneStepWindow() {
        Instant now = Instant.ofEpochSecond(1_234_567_890);
        long step = Totp.step(now);

        assertThat(Totp.match(SECRET, Totp.code(SECRET, step - 1), now, null)).hasValue(step - 1);
        assertThat(Totp.match(SECRET, Totp.code(SECRET, step + 1), now, null)).hasValue(step + 1);
        assertThat(Totp.match(SECRET, Totp.code(SECRET, step - 2), now, null)).isEmpty();
        assertThat(Totp.match(SECRET, Totp.code(SECRET, step + 2), now, null)).isEmpty();
    }

    @Test
    @DisplayName("마지막으로 받은 단계와 그 앞 단계의 코드는 다시 받지 않는다")
    void rejectsReplayedStep() {
        Instant now = Instant.ofEpochSecond(1_234_567_890);
        long step = Totp.step(now);

        assertThat(Totp.match(SECRET, Totp.code(SECRET, step), now, step)).isEmpty();
        assertThat(Totp.match(SECRET, Totp.code(SECRET, step - 1), now, step)).isEmpty();
        assertThat(Totp.match(SECRET, Totp.code(SECRET, step + 1), now, step)).hasValue(step + 1);
    }

    @Test
    @DisplayName("6자리 숫자가 아니면 받지 않는다")
    void rejectsMalformedCode() {
        Instant now = Instant.ofEpochSecond(59);

        assertThat(Totp.match(SECRET, "28708", now, null)).isEmpty();
        assertThat(Totp.match(SECRET, "2870820", now, null)).isEmpty();
        assertThat(Totp.match(SECRET, "abcdef", now, null)).isEmpty();
    }
}
