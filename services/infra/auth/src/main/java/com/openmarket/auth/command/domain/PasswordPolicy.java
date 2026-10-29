package com.openmarket.auth.command.domain;

import java.nio.charset.StandardCharsets;

/**
 * 비밀번호 규칙(ACC-1): 10자 이상, 영문과 숫자를 모두 포함한다.
 *
 * <p>bcrypt는 72바이트까지만 받는다(Spring Security는 넘으면 예외를 던진다). 글자 수는 64자까지, UTF-8 길이는 72바이트까지 받는다. 한글은 한
 * 글자가 3바이트라 64자보다 먼저 바이트 상한에 닿는다.
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 10;
    private static final int MAX_LENGTH = 64;
    private static final int MAX_BYTES = 72;

    private PasswordPolicy() {}

    public static void check(String password) {
        int length = password.codePointCount(0, password.length());
        boolean hasLetter =
                password.chars().anyMatch(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'));
        boolean hasDigit = password.chars().anyMatch(c -> c >= '0' && c <= '9');
        boolean fitsBcrypt = password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
        if (length < MIN_LENGTH || length > MAX_LENGTH || !fitsBcrypt || !hasLetter || !hasDigit) {
            throw new InvalidPasswordException();
        }
    }
}
