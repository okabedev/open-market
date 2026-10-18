package com.openmarket.auth.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressTest {

    @Test
    @DisplayName("앞뒤 공백을 지우고 소문자로 정규화한다")
    void normalizes() {
        assertThat(EmailAddress.of("  Buyer@Example.COM ").value()).isEqualTo("buyer@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "no-at", "@example.com", "a@", "a b@example.com"})
    @DisplayName("형식이 맞지 않으면 거절한다")
    void rejectsInvalid(String raw) {
        assertThatThrownBy(() -> EmailAddress.of(raw)).isInstanceOf(InvalidEmailException.class);
    }
}
