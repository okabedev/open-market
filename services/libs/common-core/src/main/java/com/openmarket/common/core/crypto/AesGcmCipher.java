package com.openmarket.common.core.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * DB에 저장할 비밀 값을 AES-256-GCM으로 암호화한다. 결과는 Base64(IV 12바이트 + 암호문과 태그)다. 같은 값도 IV가 달라 매번 다르게 암호화된다.
 *
 * <p>키는 환경 변수(.env)의 Base64 32바이트 값이다. 키를 바꾸면 이전에 암호화한 값을 복호화하지 못한다. 쓰는 곳: auth 서명 키, member 정산 계좌
 * 번호.
 */
public final class AesGcmCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final String keyName;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param base64Key Base64로 쓴 32바이트 키
     * @param keyName 오류 메시지에 쓸 환경 변수 이름. 예: {@code MEMBER_ACCOUNT_NUMBER_KEY}
     */
    public AesGcmCipher(String base64Key, String keyName) {
        this.keyName = keyName;
        byte[] bytes = Base64.getDecoder().decode(base64Key);
        if (bytes.length != KEY_BYTES) {
            throw new IllegalArgumentException(
                    keyName + "은 Base64로 쓴 32바이트여야 한다(openssl rand -base64 32)");
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
            throw new IllegalStateException("암호화하지 못했다", e);
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
            throw new IllegalStateException("복호화하지 못했다. " + keyName + "이 암호화할 때와 같은지 확인한다", e);
        }
    }
}
