package com.openmarket.auth.command.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 인증 메일 발송 요청({@code auth.account}). notification이 메일을 보낸다.
 *
 * @param verificationUrl 일회용 토큰이 든 인증 링크. 로그에 남기지 않는다
 */
public record EmailVerificationRequested(
        UUID accountId, String email, String verificationUrl, Instant expiresAt) {

    public static final String TYPE = "EmailVerificationRequested";

    @Override
    public String toString() {
        return "EmailVerificationRequested[accountId=" + accountId + "]";
    }
}
