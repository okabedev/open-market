package com.openmarket.auth.infra.security;

import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;

/**
 * 클라이언트 설정 {@value #SETTING}에 적힌 역할이 없는 계정의 인가 요청을 {@code access_denied}로 돌려보낸다. admin 클라이언트가
 * ADMIN을 요구한다(docs/architecture/auth.md "토큰").
 *
 * <p>인가 엔드포인트는 로그인 전에도 이 검사를 부른다. 그때는 통과시키고, 로그인 뒤 다시 들어올 때 검사한다.
 */
public class RequiredRoleValidator
        implements Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> {

    static final String SETTING = "openmarket.required-role";

    @Override
    public void accept(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        @Nullable String requiredRole =
                context.getRegisteredClient().getClientSettings().getSetting(SETTING);
        OAuth2AuthorizationCodeRequestAuthenticationToken request = context.getAuthentication();
        if (requiredRole == null
                || !(request.getPrincipal() instanceof Authentication principal)
                || !principal.isAuthenticated()) {
            return;
        }
        boolean granted =
                principal.getAuthorities().stream()
                        .anyMatch(a -> ("ROLE_" + requiredRole).equals(a.getAuthority()));
        if (!granted) {
            throw new OAuth2AuthorizationCodeRequestAuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED, "이 앱을 쓸 권한이 없습니다.", null),
                    request);
        }
    }
}
