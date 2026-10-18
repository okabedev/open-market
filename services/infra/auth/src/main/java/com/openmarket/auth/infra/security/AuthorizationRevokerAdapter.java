package com.openmarket.auth.infra.security;

import com.openmarket.auth.command.application.AuthorizationRevokerPort;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Component;

/**
 * 계정의 인가 기록마다 리프레시 토큰을 무효로 표시한다. 행은 지우지 않는다. OIDC 로그아웃이 {@code id_token_hint}로 인가 기록을 찾기 때문이다(지우면
 * 탈퇴 뒤 로그아웃이 400이 된다). 행의 {@code principal_name}은 accountId다({@code AccountUserDetailsService}).
 */
@Component
class AuthorizationRevokerAdapter implements AuthorizationRevokerPort {

    private final JdbcClient jdbc;
    private final OAuth2AuthorizationService authorizations;

    AuthorizationRevokerAdapter(JdbcClient jdbc, OAuth2AuthorizationService authorizations) {
        this.jdbc = jdbc;
        this.authorizations = authorizations;
    }

    @Override
    public void revokeAll(UUID accountId) {
        List<@Nullable String> ids =
                jdbc.sql("SELECT id FROM oauth2_authorization WHERE principal_name = :principal")
                        .param("principal", accountId.toString())
                        .query(String.class)
                        .list();
        for (String id : ids) {
            OAuth2Authorization authorization = id == null ? null : authorizations.findById(id);
            if (authorization == null) {
                continue;
            }
            OAuth2Authorization.Token<OAuth2RefreshToken> refresh = authorization.getRefreshToken();
            if (refresh == null || refresh.isInvalidated()) {
                continue;
            }
            authorizations.save(
                    OAuth2Authorization.from(authorization)
                            .token(
                                    refresh.getToken(),
                                    metadata ->
                                            metadata.put(
                                                    OAuth2Authorization.Token
                                                            .INVALIDATED_METADATA_NAME,
                                                    true))
                            .build());
        }
    }
}
