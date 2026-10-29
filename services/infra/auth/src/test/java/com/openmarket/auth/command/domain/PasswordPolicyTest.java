package com.openmarket.auth.command.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"abcdefgh1", "abcdefghij", "1234567890", "가나다라마바사아자차1"})
    @DisplayName("10자 미만이거나 영문·숫자가 모두 들어 있지 않으면 거절한다")
    void rejectsWeak(String password) {
        assertThatThrownBy(() -> PasswordPolicy.check(password))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    @DisplayName("64자를 넘으면 거절한다(bcrypt는 72바이트 뒤를 버린다)")
    void rejectsTooLong() {
        assertThatThrownBy(() -> PasswordPolicy.check("a1".repeat(33)))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    @DisplayName("64자 안이라도 UTF-8로 72바이트를 넘으면 거절한다(bcrypt가 받지 않는다)")
    void rejectsOverBcryptBytes() {
        String password = "가".repeat(24) + "a1";

        assertThatThrownBy(() -> PasswordPolicy.check(password))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdefghi1", "Password12!", "1a1a1a1a1a", "비밀번호는길게써도된다a1"})
    @DisplayName("10자 이상이고 영문과 숫자를 포함하면 통과한다")
    void acceptsStrong(String password) {
        assertThatCode(() -> PasswordPolicy.check(password)).doesNotThrowAnyException();
    }
}
