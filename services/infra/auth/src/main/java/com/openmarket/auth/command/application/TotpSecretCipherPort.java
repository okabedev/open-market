package com.openmarket.auth.command.application;

/** TOTP 비밀 값 암호화(AES-256-GCM, 키 {@code AUTH_TOTP_SECRET_KEY}). 비밀 값은 암호화해서만 저장한다. */
public interface TotpSecretCipherPort {

    String encrypt(byte[] secret);

    byte[] decrypt(String encrypted);
}
