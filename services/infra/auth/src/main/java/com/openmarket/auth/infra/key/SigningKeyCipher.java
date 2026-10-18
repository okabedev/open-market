package com.openmarket.auth.infra.key;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 서명 개인 키를 DB에 저장하기 전에 AES-256-GCM으로 암호화한다. 결과는 Base64(IV 12바이트 + 암호문과 태그)다.
 *
 * <p>비밀 값은 {@code AUTH_SIGNING_KEY_SECRET}(.env, Base64 32바이트)이다. 바꾸면 기존 키를 복호화하지 못해 auth가 뜨지 않는다.
 */
public final class SigningKeyCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SigningKeyCipher(String base64Secret) {
        byte[] bytes = Base64.getDecoder().decode(base64Secret);
        if (bytes.length != KEY_BYTES) {
            throw new IllegalArgumentException(
                    "AUTH_SIGNING_KEY_SECRET은 Base64로 쓴 32바이트여야 한다(openssl rand -base64 32)");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(byte[] plain) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain);
            return Base64.getEncoder()
                    .encodeToString(
                            ByteBuffer.allocate(iv.length + encrypted.length)
                                    .put(iv)
                                    .put(encrypted)
                                    .array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("서명 키를 암호화하지 못했다", e);
        }
    }

    public byte[] decrypt(String encoded) {
        ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encoded));
        byte[] iv = new byte[IV_BYTES];
        buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(encrypted);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "서명 키를 복호화하지 못했다. AUTH_SIGNING_KEY_SECRET이 키를 만들 때와 같은지 확인한다", e);
        }
    }
}
