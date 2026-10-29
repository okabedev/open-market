package com.openmarket.auth.command.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 로그인 보안 이벤트를 남긴다(docs/architecture/observability.md "보안 이벤트"). 이메일 원문은 넘기지 않는다(NFR-7). IP는 연결 주소다.
 */
public interface SecurityEventPort {

    /** 비밀번호가 틀렸다. 없는 계정이면 {@code accountId}가 null이다. */
    void loginFailed(@Nullable UUID accountId, @Nullable String ip);

    /** 이번 실패로 계정을 잠갔다(5회, 15분). */
    void accountLocked(UUID accountId, @Nullable String ip);

    /** 2단계 코드(또는 복구 코드)가 틀렸다. */
    void secondFactorFailed(UUID accountId, @Nullable String ip);

    /** 한 IP가 분당 로그인 시도 한도를 넘었다. 창마다 처음 넘을 때만 부른다. */
    void loginRateLimited(@Nullable String ip);
}
