package com.openmarket.auth.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TotpCredentialTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_234_567_890);
    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final List<String> HASHES = List.of("h-1", "h-2");

    private static String code(long stepOffset) {
        return Totp.code(SECRET, Totp.step(NOW) + stepOffset);
    }

    @Test
    @DisplayName("등록은 맞는 코드로만 확인되고, 확인하면 ACTIVE가 되며 그 단계는 다시 받지 않는다")
    void confirmsWithValidCodeOnly() {
        TotpCredential credential = TotpCredential.pending(UUID.randomUUID(), "enc", NOW);

        assertThat(credential.confirm(SECRET, "000000", HASHES, NOW)).isFalse();
        assertThat(credential.isActive()).isFalse();

        assertThat(credential.confirm(SECRET, code(0), HASHES, NOW)).isTrue();
        assertThat(credential.isActive()).isTrue();
        assertThat(credential.enrolledAt()).isEqualTo(NOW);
        assertThat(credential.verify(SECRET, code(0), NOW)).isFalse();
        assertThat(credential.verify(SECRET, code(1), NOW)).isTrue();
    }

    @Test
    @DisplayName("ACTIVE 등록은 다시 확인할 수 없고, 등록 전에는 코드를 확인할 수 없다")
    void guardsStatus() {
        TotpCredential credential = TotpCredential.pending(UUID.randomUUID(), "enc", NOW);
        assertThatThrownBy(() -> credential.verify(SECRET, code(0), NOW))
                .isInstanceOf(IllegalStateException.class);

        credential.confirm(SECRET, code(0), HASHES, NOW);

        assertThatThrownBy(() -> credential.confirm(SECRET, code(1), HASHES, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("복구 코드는 맞는 것을 한 번만 쓴다")
    void usesRecoveryCodeOnce() {
        TotpCredential credential = TotpCredential.pending(UUID.randomUUID(), "enc", NOW);
        credential.confirm(SECRET, code(0), HASHES, NOW);

        assertThat(credential.useRecoveryCode("h-2"::equals, NOW)).isTrue();
        assertThat(credential.useRecoveryCode("h-2"::equals, NOW)).isFalse();
        assertThat(credential.useRecoveryCode("h-9"::equals, NOW)).isFalse();
        assertThat(credential.remainingRecoveryCodes()).isEqualTo(1);
    }

    @Test
    @DisplayName("복구 코드는 10개, 5자-5자 Base32 문자이고 입력은 대소문자·하이픈·공백을 무시해 맞춘다")
    void generatesRecoveryCodes() {
        List<String> codes = RecoveryCode.generate(new java.security.SecureRandom());

        assertThat(codes).hasSize(10).doesNotHaveDuplicates();
        assertThat(codes).allMatch(c -> c.matches("[A-Z2-7]{5}-[A-Z2-7]{5}"));
        assertThat(RecoveryCode.normalize(" abcde-fghij ")).isEqualTo("ABCDEFGHIJ");
        assertThat(RecoveryCode.looksLike("ABCDE-FGHIJ")).isTrue();
        assertThat(RecoveryCode.looksLike("123456")).isFalse();
    }
}
