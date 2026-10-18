package com.openmarket.auth.command.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 이메일 인증 토큰. 원문(256비트 무작위, Base64URL)은 인증 링크로만 나가고, 저장은 SHA-256 해시로 한다.
 *
 * @param raw 링크에 넣는 원문. 로그에 남기지 않는다
 */
public record VerificationToken(String raw) {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static VerificationToken generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return new VerificationToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    public static String hash(String raw) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String hash() {
        return hash(raw);
    }

    @Override
    public String toString() {
        return "VerificationToken[***]";
    }
}
