package com.openmarket.auth.infra;

import com.openmarket.auth.command.domain.Role;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * auth 설정({@code openmarket.auth}). 값은 config-repo auth.yml에 있고, 비밀 값은 .env 자리 표시자다.
 *
 * @param issuer 토큰 {@code iss}와 discovery 주소. 요청 주소와 상관없이 고정한다(docs/architecture/auth.md "issuer
 *     호스트 이름")
 * @param signingKeySecret 서명 개인 키 암호화 키(Base64 32바이트)
 * @param accessTokenTtl 액세스 토큰 수명
 * @param adminSeed 관리자 시드 계정. 이메일이 비어 있으면 만들지 않는다
 * @param userClients BFF(Next.js 앱)용 기밀 클라이언트. 키가 클라이언트 ID다
 * @param serviceClients 서비스 간 호출용 {@code client_credentials} 클라이언트. 키가 서비스 이름이다
 */
@ConfigurationProperties("openmarket.auth")
public record AuthProperties(
        URI issuer,
        String signingKeySecret,
        @DefaultValue("10m") Duration accessTokenTtl,
        @DefaultValue AdminSeed adminSeed,
        @DefaultValue Map<String, UserClient> userClients,
        @DefaultValue Map<String, ServiceClient> serviceClients) {

    public record AdminSeed(@DefaultValue("") String email, @DefaultValue("") String password) {

        public boolean isConfigured() {
            return !email.isBlank() && !password.isBlank();
        }

        @Override
        public String toString() {
            return "AdminSeed[***]";
        }
    }

    /**
     * BFF 클라이언트 하나.
     *
     * @param requiredRole 이 역할이 없는 계정의 인가 요청은 거절한다(admin 앱은 ADMIN)
     */
    public record UserClient(
            String secret,
            URI redirectUri,
            URI postLogoutRedirectUri,
            Duration refreshTokenTtl,
            @Nullable Role requiredRole) {

        @Override
        public String toString() {
            return "UserClient[redirectUri=" + redirectUri + ", requiredRole=" + requiredRole + "]";
        }
    }

    public record ServiceClient(String secret) {

        @Override
        public String toString() {
            return "ServiceClient[***]";
        }
    }
}
