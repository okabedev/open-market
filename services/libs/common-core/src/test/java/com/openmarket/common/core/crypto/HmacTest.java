package com.openmarket.common.core.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HmacTest {

    @Test
    @DisplayName("HMAC-SHA1은 RFC 2202 시험 벡터와 같다")
    void sha1MatchesRfc2202() {
        byte[] key = "Jefe".getBytes(StandardCharsets.US_ASCII);
        byte[] data = "what do ya want for nothing?".getBytes(StandardCharsets.US_ASCII);

        assertThat(HexFormat.of().formatHex(Hmac.sha1(key, data)))
                .isEqualTo("effcdf6ae5eb2fa2d27416d5f184df9c259a7c79");
    }
}
