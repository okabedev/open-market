package com.openmarket.auth.infra.security;

import com.openmarket.auth.infra.AuthProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * 설정({@code openmarket.auth.user-clients}, {@code service-clients})의 클라이언트를 DB에 맞춘다. 기동할 때마다 실행하고,
 * 다시 실행해도 결과가 같다(행 ID = 클라이언트 ID). 시크릿은 바뀌었을 때만 다시 해시한다.
 *
 * <p>설정에서 뺀 클라이언트는 지우지 않는다. 지우려면 Flyway 마이그레이션으로 지운다.
 */
public class RegisteredClientSynchronizer {

    static final String API_SCOPE = "api";
    static final String INTERNAL_SCOPE = "internal";

    private final RegisteredClientRepository clients;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    public RegisteredClientSynchronizer(
            RegisteredClientRepository clients,
            PasswordEncoder passwordEncoder,
            AuthProperties properties,
            Clock clock) {
        this.clients = clients;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public void synchronize() {
        for (Map.Entry<String, AuthProperties.UserClient> entry :
                properties.userClients().entrySet()) {
            clients.save(userClient(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, AuthProperties.ServiceClient> entry :
                properties.serviceClients().entrySet()) {
            clients.save(serviceClient(entry.getKey(), entry.getValue()));
        }
    }

    private RegisteredClient userClient(String clientId, AuthProperties.UserClient client) {
        ClientSettings.Builder settings =
                ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false);
        if (client.requiredRole() != null) {
            settings.setting(RequiredRoleValidator.SETTING, client.requiredRole().name());
        }
        return base(clientId, client.secret())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(client.redirectUri().toString())
                .postLogoutRedirectUri(client.postLogoutRedirectUri().toString())
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(API_SCOPE)
                .clientSettings(settings.build())
                .tokenSettings(
                        tokenSettings()
                                .refreshTokenTimeToLive(client.refreshTokenTtl())
                                // 리프레시 토큰은 쓸 때마다 새로 발급한다(rotation).
                                .reuseRefreshTokens(false)
                                .build())
                .build();
    }

    private RegisteredClient serviceClient(String clientId, AuthProperties.ServiceClient client) {
        return base(clientId, client.secret())
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(INTERNAL_SCOPE)
                .clientSettings(ClientSettings.builder().build())
                .tokenSettings(tokenSettings().build())
                .build();
    }

    private RegisteredClient.Builder base(String clientId, String secret) {
        @Nullable RegisteredClient existing = clients.findByClientId(clientId);
        String storedSecret =
                existing != null
                                && existing.getClientSecret() != null
                                && passwordEncoder.matches(secret, existing.getClientSecret())
                        ? existing.getClientSecret()
                        : passwordEncoder.encode(secret);
        return RegisteredClient.withId(clientId)
                .clientId(clientId)
                .clientIdIssuedAt(
                        existing != null && existing.getClientIdIssuedAt() != null
                                ? existing.getClientIdIssuedAt()
                                : clock.instant())
                .clientName(clientId)
                .clientSecret(storedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
    }

    private TokenSettings.Builder tokenSettings() {
        Duration ttl = properties.accessTokenTtl();
        return TokenSettings.builder().accessTokenTimeToLive(ttl);
    }
}
