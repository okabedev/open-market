package com.openmarket.member.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesGcmAccountNumberCipherAdapterTest {

    @Test
    @DisplayName("암호화한 계좌 번호는 같은 키로 복호화하면 원래 번호다")
    void roundTrips() {
        AesGcmAccountNumberCipherAdapter cipher =
                new AesGcmAccountNumberCipherAdapter(
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

        String encrypted = cipher.encrypt("110123456789");

        assertThat(encrypted).doesNotContain("110123456789");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("110123456789");
    }
}
