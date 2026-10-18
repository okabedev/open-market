package com.openmarket.auth.infra.persistence;

import com.openmarket.auth.command.application.AuthorizationRevokerPort;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * {@code oauth2_authorization}에서 계정의 행을 지운다. 행의 {@code principal_name}은 accountId다({@code
 * AccountUserDetailsService}). 호출한 쪽의 트랜잭션에 참여한다.
 */
@Component
class JdbcAuthorizationRevokerAdapter implements AuthorizationRevokerPort {

    private final JdbcClient jdbc;

    JdbcAuthorizationRevokerAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void revokeAll(UUID accountId) {
        jdbc.sql("DELETE FROM oauth2_authorization WHERE principal_name = :principal")
                .param("principal", accountId.toString())
                .update();
    }
}
