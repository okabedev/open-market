package com.openmarket.auth.command.domain;

import java.util.Optional;

/** 이메일 인증 저장소. 구현은 {@code infra}에 있다. */
public interface EmailVerificationRepository {

    Optional<EmailVerification> findByTokenHash(String tokenHash);

    EmailVerification save(EmailVerification verification);
}
