package com.openmarket.auth.infra.security;

import com.openmarket.auth.command.application.RecoveryCodeHasherPort;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 복구 코드를 비밀번호와 같은 {@link PasswordEncoder}(bcrypt)로 해시한다. */
public final class PasswordEncoderRecoveryCodeHasherAdapter implements RecoveryCodeHasherPort {

    private final PasswordEncoder encoder;

    public PasswordEncoderRecoveryCodeHasherAdapter(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(String code) {
        return encoder.encode(code);
    }

    @Override
    public boolean matches(String code, String hash) {
        return encoder.matches(code, hash);
    }
}
