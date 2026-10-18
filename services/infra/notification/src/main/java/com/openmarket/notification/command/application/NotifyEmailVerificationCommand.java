package com.openmarket.notification.command.application;

import java.time.Instant;
import java.util.UUID;

/**
 * 인증 메일 알림. {@code EmailVerificationRequested}에서 만든다.
 *
 * @param sourceEventId 원인 이벤트 ID. 중복 방지 키다
 * @param verificationUrl 일회용 토큰이 든 링크. 로그에 남기지 않는다
 */
public record NotifyEmailVerificationCommand(
        UUID sourceEventId,
        UUID accountId,
        String email,
        String verificationUrl,
        Instant expiresAt) {

    @Override
    public String toString() {
        return "NotifyEmailVerificationCommand[sourceEventId=" + sourceEventId + "]";
    }
}
