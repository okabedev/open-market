package com.openmarket.auth.command.application;

import java.util.UUID;

/** 계정의 리프레시 토큰을 모두 무효로 한다. 그 뒤에는 토큰을 갱신할 수 없다. 남은 액세스 토큰은 수명(10분)까지 유효하다. */
public interface AuthorizationRevokerPort {

    void revokeAll(UUID accountId);
}
