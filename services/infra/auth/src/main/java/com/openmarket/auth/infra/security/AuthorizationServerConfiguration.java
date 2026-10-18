package com.openmarket.auth.infra.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.infra.AuthProperties;
import com.openmarket.auth.infra.key.JdbcSigningKeyStore;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * Spring Authorization Server 구성(docs/architecture/auth.md). 클라이언트, 인가, 동의는 auth 스키마에 JDBC로 저장한다.
 *
 * <p>필터 체인은 둘이다. 하나는 OAuth2·OIDC 엔드포인트, 다른 하나는 로그인 화면과 actuator다.
 */
@Configuration(proxyBeanMethods = false)
public class AuthorizationServerConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) {
        http.oauth2AuthorizationServer(
                        server -> {
                            http.securityMatcher(server.getEndpointsMatcher());
                            server.authorizationEndpoint(
                                            endpoint ->
                                                    endpoint.authenticationProviders(
                                                            AuthorizationServerConfiguration
                                                                    ::addRequiredRoleCheck))
                                    .oidc(Customizer.withDefaults());
                        })
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                // 브라우저가 로그인 없이 인가 엔드포인트에 오면 로그인 화면으로 보낸다.
                .exceptionHandling(
                        exceptions ->
                                exceptions.defaultAuthenticationEntryPointFor(
                                        new LoginUrlAuthenticationEntryPoint("/login"),
                                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) {
        http.authorizeHttpRequests(
                        authorize ->
                                authorize
                                        // actuator는 관리 포트(9001)에만 있고 호스트에 열지 않는다.
                                        .requestMatchers(EndpointRequest.toAnyEndpoint())
                                        .permitAll()
                                        .requestMatchers("/error")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .formLogin(Customizer.withDefaults());
        return http.build();
    }

    private static void addRequiredRoleCheck(List<AuthenticationProvider> providers) {
        for (var provider : providers) {
            if (provider
                    instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider codeRequest) {
                codeRequest.setAuthenticationValidator(
                        new OAuth2AuthorizationCodeRequestAuthenticationValidator()
                                .andThen(new RequiredRoleValidator()));
            }
        }
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(AuthProperties properties) {
        // issuer를 고정해 요청 주소(localhost, 컨테이너 이름)와 상관없이 같은 iss를 쓴다(R7).
        return AuthorizationServerSettings.builder().issuer(properties.issuer().toString()).build();
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(JdbcOperations jdbc) {
        return new JdbcRegisteredClientRepository(jdbc);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(
            JdbcOperations jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbc, clients);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcOperations jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }

    @Bean
    SigningKeys signingKeys(JdbcSigningKeyStore store) {
        return SigningKeys.load(store);
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(SigningKeys keys) {
        return new ImmutableJWKSet<>(keys.jwkSet());
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> tokenClaimsCustomizer(
            AccountRepository accounts, SigningKeys keys) {
        return new TokenClaimsCustomizer(accounts, keys::activeKeyId);
    }

    @Bean
    UserDetailsService userDetailsService(AccountRepository accounts) {
        return new AccountUserDetailsService(accounts);
    }

    /** 기동할 때 읽은 서명 키. 서명은 ACTIVE 키 하나로 하고, JWK 세트에는 RETIRED가 아닌 키를 모두 싣는다. */
    public record SigningKeys(String activeKeyId, JWKSet jwkSet) {

        static SigningKeys load(JdbcSigningKeyStore store) {
            RSAKey active = store.loadOrCreate();
            List<JWK> keys = new ArrayList<>(store.published());
            return new SigningKeys(active.getKeyID(), new JWKSet(keys));
        }
    }
}
