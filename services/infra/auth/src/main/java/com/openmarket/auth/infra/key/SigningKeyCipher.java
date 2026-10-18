package com.openmarket.auth.infra.key;

import com.openmarket.common.core.crypto.AesGcmCipher;

/**
 * 서명 개인 키를 DB에 저장하기 전에 AES-256-GCM으로 암호화한다({@link AesGcmCipher}).
 *
 * <p>비밀 값은 {@code AUTH_SIGNING_KEY_SECRET}(.env, Base64 32바이트)이다. 바꾸면 기존 키를 복호화하지 못해 auth가 뜨지 않는다.
 */
public final class SigningKeyCipher {

    private final AesGcmCipher cipher;

    public SigningKeyCipher(String base64Secret) {
        this.cipher = new AesGcmCipher(base64Secret, "AUTH_SIGNING_KEY_SECRET");
    }

    public String encrypt(byte[] plain) {
        return cipher.encrypt(plain);
    }

    public byte[] decrypt(String encoded) {
        return cipher.decrypt(encoded);
    }
}
