package com.openmarket.auth.command.domain;

/**
 * 비밀번호 규칙(ACC-1): 10자 이상, 영문과 숫자를 모두 포함한다.
 *
 * <p>64자 상한은 bcrypt가 72바이트 뒤를 버리기 때문이다. 한글처럼 한 글자가 여러 바이트인 문자를 써도 72바이트 안에 든다.
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 10;
    private static final int MAX_LENGTH = 64;

    private PasswordPolicy() {}

    public static void check(String password) {
        int length = password.codePointCount(0, password.length());
        boolean hasLetter =
                password.chars().anyMatch(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'));
        boolean hasDigit = password.chars().anyMatch(c -> c >= '0' && c <= '9');
        if (length < MIN_LENGTH || length > MAX_LENGTH || !hasLetter || !hasDigit) {
            throw new InvalidPasswordException();
        }
    }
}
