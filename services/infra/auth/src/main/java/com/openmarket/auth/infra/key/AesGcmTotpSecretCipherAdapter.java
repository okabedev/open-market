package com.openmarket.auth.infra.key;

import com.openmarket.auth.command.application.TotpSecretCipherPort;
import com.openmarket.common.core.crypto.AesGcmCipher;

/**
 * TOTP 비밀 값을 AES-256-GCM으로 암호화한다({@link AesGcmCipher}, 서명 키와 같은 방식).
 *
 * <p>키는 {@code AUTH_TOTP_SECRET_KEY}(.env, Base64 32바이트)다. 서명 키와 따로 둔다. 바꾸면 등록한 관리자가 로그인하지 못하므로 런북의
 * 등록 해제 절차로 다시 등록한다.
 */
public final class AesGcmTotpSecretCipherAdapter implements TotpSecretCipherPort {

    private final AesGcmCipher cipher;

    public AesGcmTotpSecretCipherAdapter(String base64Key) {
        this.cipher = new AesGcmCipher(base64Key, "AUTH_TOTP_SECRET_KEY");
    }

    @Override
    public String encrypt(byte[] secret) {
        return cipher.encrypt(secret);
    }

    @Override
    public byte[] decrypt(String encrypted) {
        return cipher.decrypt(encrypted);
    }
}
