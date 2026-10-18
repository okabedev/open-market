package com.openmarket.common.core.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesGcmCipherTest {

    static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    static final String OTHER_KEY =
            Base64.getEncoder()
                    .encodeToString(
                            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("암호화한 값을 같은 키로 복호화하면 원래 값이다. 같은 값도 매번 다르게 암호화된다")
    void roundTrips() {
        AesGcmCipher cipher = new AesGcmCipher(KEY, "TEST_KEY");
        byte[] plain = "110-123-456789".getBytes(StandardCharsets.UTF_8);

        String first = cipher.encrypt(plain);
        String second = cipher.encrypt(plain);

        assertThat(first).isNotEqualTo(second).doesNotContain("110-123-456789");
        assertThat(cipher.decrypt(first)).isEqualTo(plain);
        assertThat(cipher.decrypt(second)).isEqualTo(plain);
    }

    @Test
    @DisplayName("다른 키로는 복호화하지 못한다")
    void rejectsOtherKey() {
        String encrypted = new AesGcmCipher(KEY, "TEST_KEY").encrypt(new byte[] {1, 2, 3});

        assertThatThrownBy(() -> new AesGcmCipher(OTHER_KEY, "TEST_KEY").decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEST_KEY");
    }

    @Test
    @DisplayName("키는 Base64로 쓴 32바이트여야 하고, 오류 메시지는 환경 변수 이름을 알려 준다")
    void requires32ByteKey() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AesGcmCipher(shortKey, "TEST_KEY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TEST_KEY")
                .hasMessageContaining("openssl rand -base64 32");
    }
}
