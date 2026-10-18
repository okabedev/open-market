package com.openmarket.member.infra;

import com.openmarket.common.core.crypto.AesGcmCipher;
import com.openmarket.member.command.application.AccountNumberCipherPort;
import java.nio.charset.StandardCharsets;

/** 정산 계좌 번호를 AES-256-GCM으로 암호화한다. 키는 {@code MEMBER_ACCOUNT_NUMBER_KEY}다. */
class AesGcmAccountNumberCipherAdapter implements AccountNumberCipherPort {

    private final AesGcmCipher cipher;

    AesGcmAccountNumberCipherAdapter(String base64Key) {
        this.cipher = new AesGcmCipher(base64Key, "MEMBER_ACCOUNT_NUMBER_KEY");
    }

    @Override
    public String encrypt(String accountNumber) {
        return cipher.encrypt(accountNumber.getBytes(StandardCharsets.UTF_8));
    }

    /** 정산 지급(M5)과 테스트가 쓴다. */
    String decrypt(String encrypted) {
        return new String(cipher.decrypt(encrypted), StandardCharsets.UTF_8);
    }
}
