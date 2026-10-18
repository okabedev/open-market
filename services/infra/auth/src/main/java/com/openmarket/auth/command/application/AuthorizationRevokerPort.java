package com.openmarket.auth.command.application;

import java.util.UUID;

/** 계정의 인가 기록(액세스·리프레시 토큰)을 모두 지운다. 지운 뒤에는 리프레시 토큰으로 갱신할 수 없다. */
public interface AuthorizationRevokerPort {

    void revokeAll(UUID accountId);
}
