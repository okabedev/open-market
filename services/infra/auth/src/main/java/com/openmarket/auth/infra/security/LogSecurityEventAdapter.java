package com.openmarket.auth.infra.security;

import com.openmarket.auth.command.application.SecurityEventPort;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 보안 이벤트를 WARN 로그로 남긴다. 로거 이름은 {@code security}이고 메시지는 {@code 보안 이벤트 <이벤트> account=<계정 ID>
 * ip=<주소>}다 (docs/architecture/observability.md "보안 이벤트"). 없는 계정은 {@code unknown}이다.
 */
public class LogSecurityEventAdapter implements SecurityEventPort {

    private static final Logger log = LoggerFactory.getLogger("security");

    @Override
    public void loginFailed(@Nullable UUID accountId, @Nullable String ip) {
        log.warn(
                "보안 이벤트 LOGIN_FAILED account={} ip={}",
                accountId == null ? "unknown" : accountId,
                ip);
    }

    @Override
    public void accountLocked(UUID accountId, @Nullable String ip) {
        log.warn("보안 이벤트 ACCOUNT_LOCKED account={} ip={}", accountId, ip);
    }

    @Override
    public void secondFactorFailed(UUID accountId, @Nullable String ip) {
        log.warn("보안 이벤트 SECOND_FACTOR_FAILED account={} ip={}", accountId, ip);
    }

    @Override
    public void loginRateLimited(@Nullable String ip) {
        log.warn("보안 이벤트 LOGIN_RATE_LIMITED ip={}", ip);
    }
}
