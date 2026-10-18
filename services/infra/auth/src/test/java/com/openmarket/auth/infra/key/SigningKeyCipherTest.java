package com.openmarket.auth.infra.key;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SigningKeyCipherTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    @DisplayName("암호화한 값을 같은 비밀 값으로 복호화하면 원문이 나온다")
    void roundTrips() {
        SigningKeyCipher cipher = new SigningKeyCipher(SECRET);

        String encrypted = cipher.encrypt("private-key-bytes".getBytes(UTF_8));

        assertThat(encrypted).doesNotContain("private-key-bytes");
        assertThat(new String(cipher.decrypt(encrypted), UTF_8)).isEqualTo("private-key-bytes");
    }

    @Test
    @DisplayName("같은 원문도 매번 다른 암호문이 된다(IV 무작위)")
    void usesRandomIv() {
        SigningKeyCipher cipher = new SigningKeyCipher(SECRET);

        assertThat(cipher.encrypt(new byte[] {1})).isNotEqualTo(cipher.encrypt(new byte[] {1}));
    }

    @Test
    @DisplayName("다른 비밀 값으로는 복호화할 수 없다")
    void rejectsWrongSecret() {
        String encrypted = new SigningKeyCipher(SECRET).encrypt(new byte[] {1, 2, 3});
        byte[] other = new byte[32];
        other[0] = 1;

        assertThatThrownBy(
                        () ->
                                new SigningKeyCipher(Base64.getEncoder().encodeToString(other))
                                        .decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("비밀 값은 Base64로 쓴 32바이트여야 한다")
    void requires256BitSecret() {
        assertThatThrownBy(
                        () ->
                                new SigningKeyCipher(
                                        Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }
}
