package com.openmarket.common.core.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MaskingTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"홍길동, 홍*동", "홍길, 홍*", "홍, *", "남궁민수, 남**수"})
    @DisplayName("이름은 첫 글자와 마지막 글자만 남긴다")
    void name(String name, String expected) {
        assertThat(Masking.name(name)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "010-1234-5678, 010-****-5678",
        "01012345678, 010-****-5678",
        "02-123-4567, 02-***-4567",
        "0212345678, 02-****-5678",
        "0311234567, 031-***-4567",
        "1588-1234, ****1234"
    })
    @DisplayName("전화번호는 가운데 자리를 가린다")
    void phone(String phone, String expected) {
        assertThat(Masking.phone(phone)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "buyer@example.com, bu***@example.com",
        "a@example.com, a***@example.com",
        "invalid, ***"
    })
    @DisplayName("이메일은 아이디 앞 두 글자만 남긴다")
    void email(String email, String expected) {
        assertThat(Masking.email(email)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"110-123-456789, ********6789", "123, ***"})
    @DisplayName("계좌번호는 뒤 네 자리만 남긴다")
    void accountNumber(String account, String expected) {
        assertThat(Masking.accountNumber(account)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"'서울특별시 강남구 테헤란로 123 4층', '서울특별시 강남구 ***'", "'제주특별자치도', '제주특별자치도 ***'"})
    @DisplayName("주소는 앞의 두 단어만 남긴다")
    void address(String address, String expected) {
        assertThat(Masking.address(address)).isEqualTo(expected);
    }
}
