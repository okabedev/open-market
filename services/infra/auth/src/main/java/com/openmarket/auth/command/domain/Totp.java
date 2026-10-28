package com.openmarket.auth.command.domain;

import com.openmarket.common.core.crypto.Hmac;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/**
 * 시간 기반 일회용 코드(RFC 6238, HMAC-SHA1, 6자리, 30초). 관리자 2단계 인증이 쓴다(docs/architecture/auth.md "관리자 2단계
 * 인증"). 인증 앱(Google Authenticator 등)의 기본값과 같다.
 */
public final class Totp {

    static final int DIGITS = 6;
    static final long STEP_SECONDS = 30;

    /** 앞뒤로 받는 단계 수. 기기 시계가 조금 어긋나도 받는다. */
    static final int WINDOW = 1;

    /** 비밀 값 길이. RFC 4226이 권하는 160비트다. */
    static final int SECRET_BYTES = 20;

    private static final int MODULO = 1_000_000;

    private Totp() {}

    public static byte[] generateSecret(SecureRandom random) {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return secret;
    }

    public static long step(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), STEP_SECONDS);
    }

    public static String code(byte[] secret, long step) {
        byte[] hash = Hmac.sha1(secret, ByteBuffer.allocate(Long.BYTES).putLong(step).array());
        int offset = hash[hash.length - 1] & 0x0f;
        int binary =
                ((hash[offset] & 0x7f) << 24)
                        | ((hash[offset + 1] & 0xff) << 16)
                        | ((hash[offset + 2] & 0xff) << 8)
                        | (hash[offset + 3] & 0xff);
        return String.format("%0" + DIGITS + "d", binary % MODULO);
    }

    /**
     * 코드가 맞은 단계. 지금 단계의 앞뒤 {@value #WINDOW}단계까지 보고, {@code lastUsedStep} 이하 단계는 받지 않는다(같은 코드 재사용
     * 방지).
     */
    public static OptionalLong match(
            byte[] secret, String code, Instant now, @Nullable Long lastUsedStep) {
        if (!code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        long current = step(now);
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) {
                continue;
            }
            // 단계마다 같은 시간이 걸리게 비교한다.
            if (MessageDigest.isEqual(
                    code(secret, step).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }
}
