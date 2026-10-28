package com.openmarket.common.core.crypto;

import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC 계산. 도메인이 {@code javax.crypto}를 직접 쓰지 않게 여기에 둔다. 쓰는 곳: auth TOTP(RFC 6238, HMAC-SHA1). */
public final class Hmac {

    private Hmac() {}

    public static byte[] sha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1을 쓸 수 없다", e);
        }
    }
}
