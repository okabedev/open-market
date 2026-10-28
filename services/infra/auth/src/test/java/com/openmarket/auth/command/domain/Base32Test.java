package com.openmarket.auth.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Base32Test {

    @ParameterizedTest
    @CsvSource({
        "f, MY",
        "fo, MZXQ",
        "foo, MZXW6",
        "foob, MZXW6YQ",
        "fooba, MZXW6YTB",
        "foobar, MZXW6YTBOI"
    })
    @DisplayName("RFC 4648 시험 벡터와 같고(패딩 없음) 다시 풀면 원래 값이다")
    void matchesRfc4648Vectors(String plain, String encoded) {
        byte[] bytes = plain.getBytes(StandardCharsets.US_ASCII);

        assertThat(Base32.encode(bytes)).isEqualTo(encoded);
        assertThat(Base32.decode(encoded)).isEqualTo(bytes);
    }

    @ParameterizedTest
    @CsvSource({"mzxw6ytboi", "MZXW 6YTB OI", "MZXW6YTBOI======"})
    @DisplayName("소문자, 공백, 패딩은 받는다")
    void decodesLenientInput(String encoded) {
        assertThat(Base32.decode(encoded)).isEqualTo("foobar".getBytes(StandardCharsets.US_ASCII));
    }

    @ParameterizedTest
    @CsvSource({"MZXW1", "MZ-XW"})
    @DisplayName("Base32 문자가 아니면 거절한다")
    void rejectsInvalidCharacters(String encoded) {
        assertThatThrownBy(() -> Base32.decode(encoded))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
