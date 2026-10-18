package com.openmarket.auth.infra.security;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.transaction.annotation.Transactional;

/**
 * 토큰 클레임(docs/architecture/auth.md "토큰").
 *
 * <ul>
 *   <li>모든 JWT 헤더에 서명 키 ID를 넣는다. JWK 세트에 키가 여럿이어도 서명 키를 하나로 고른다.
 *   <li>서비스 토큰({@code client_credentials}): {@code aud=internal}.
 *   <li>사용자 액세스 토큰: {@code roles}. 갱신할 때도 DB에서 다시 읽어, 입점 승인·정지가 다음 갱신부터 반영된다(SEL-2).
 * </ul>
 */
public class TokenClaimsCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    static final String INTERNAL_AUDIENCE = "internal";

    private final AccountRepository accounts;
    private final Supplier<String> signingKeyId;

    public TokenClaimsCustomizer(AccountRepository accounts, Supplier<String> signingKeyId) {
        this.accounts = accounts;
        this.signingKeyId = signingKeyId;
    }

    @Override
    @Transactional(readOnly = true)
    public void customize(JwtEncodingContext context) {
        context.getJwsHeader().keyId(signingKeyId.get());
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
            context.getClaims().audience(new ArrayList<>(List.of(INTERNAL_AUDIENCE)));
            return;
        }
        Authentication principal = context.getPrincipal();
        if (principal == null) {
            return;
        }
        // 클레임은 인가 정보(oauth2_authorization)에 JSON으로 저장됐다가 갱신·폐기할 때 다시 읽힌다. Spring Security Jackson
        // 모듈이
        // 허용하는 타입이어야 해서 List.of·toList()의 불변 리스트 대신 ArrayList를 쓴다.
        ArrayList<String> roles = new ArrayList<>();
        accounts.findById(UUID.fromString(principal.getName()))
                .filter(Account::isActive)
                .ifPresent(
                        account ->
                                account.roles().stream()
                                        .map(Enum::name)
                                        .sorted()
                                        .forEach(roles::add));
        context.getClaims().claim("roles", roles);
    }
}
