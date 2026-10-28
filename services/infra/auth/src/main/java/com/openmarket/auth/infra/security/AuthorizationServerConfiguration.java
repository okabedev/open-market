package com.openmarket.auth.infra.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.openmarket.auth.command.application.RecordLoginFailureUseCase;
import com.openmarket.auth.command.application.RecordLoginSuccessUseCase;
import com.openmarket.auth.command.application.SecondFactorSessionPort;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.infra.AuthProperties;
import com.openmarket.auth.infra.key.JdbcSigningKeyStore;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authorization.AuthorizationManagerFactories;
import org.springframework.security.authorization.AuthorizationManagerFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.http.SessionCreationPolicy;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Spring Authorization Server 구성(docs/architecture/auth.md). 클라이언트, 인가, 동의는 auth 스키마에 JDBC로 저장한다.
 *
 * <p>필터 체인은 셋이다. 로그인 세션을 쓰는 두 체인(1, 3)은 비활성화된 계정의 세션을 끝낸다({@link DeactivatedAccountSessionFilter}).
 *
 * <p>ADMIN 역할 로그인은 비밀번호 다음에 TOTP를 확인해야 인가 코드를 받는다(ADR 0024). 체인 1의 권한 규칙이 {@link
 * SecondFactor#AUTHORITY} 요소를 요구하고, 없으면 2단계 화면으로 보낸다. 인가 코드를 내는 필터는 권한 검사({@code
 * AuthorizationFilter}) 뒤에 있어 비밀번호만 맞힌 세션은 코드를 받지 못한다.
 *
 * <ol>
 *   <li>OAuth2·OIDC 엔드포인트
 *   <li>가입 API({@code /v1/**}): 세션·CSRF 없이 공개 경로만 연다. BFF 서버가 gateway를 거쳐 부른다. 관리자 API({@code
 *       /v1/admin/**}, DLT 메시지)는 auth가 발급한 액세스 토큰의 {@code roles}에 ADMIN이 있어야 한다
 *   <li>로그인 화면, actuator, OpenAPI 문서
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
public class AuthorizationServerConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http, AccountRepository accounts) {
        http.addFilterAfter(
                        new DeactivatedAccountSessionFilter(accounts),
                        SecurityContextHolderFilter.class)
                .oauth2AuthorizationServer(
                        server -> {
                            http.securityMatcher(server.getEndpointsMatcher());
                            server.authorizationEndpoint(
                                            endpoint ->
                                                    endpoint.authenticationProviders(
                                                            AuthorizationServerConfiguration
                                                                    ::addRequiredRoleCheck))
                                    .oidc(Customizer.withDefaults());
                        })
                .authorizeHttpRequests(
                        authorize -> authorize.anyRequest().access(secondFactor().authenticated()))
                // 브라우저가 로그인 없이 인가 엔드포인트에 오면 로그인 화면으로, 관리자가 2단계를 마치지 않았으면 2단계 화면으로
                // 보낸다. 2단계 화면으로 보낼 때 인가 요청을 저장해 확인 뒤 돌아온다.
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .defaultAuthenticationEntryPointFor(
                                                new LoginUrlAuthenticationEntryPoint("/login"),
                                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML))
                                        .defaultDeniedHandlerForMissingAuthority(
                                                new LoginUrlAuthenticationEntryPoint(
                                                        SecondFactor.PAGE),
                                                SecondFactor.AUTHORITY));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) {
        http.securityMatcher("/v1/**")
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/v1/accounts",
                                                "/v1/accounts/email-verification",
                                                "/v1/accounts/email-verification/resend")
                                        .permitAll()
                                        .requestMatchers("/v1/admin/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .denyAll())
                // 관리자 API는 다른 서비스와 같게 액세스 토큰으로 인증한다(common-security와 같은 roles → ROLE_ 변환).
                .oauth2ResourceServer(
                        resource ->
                                resource.jwt(
                                        jwt ->
                                                jwt.decoder(jwtDecoder)
                                                        .jwtAuthenticationConverter(
                                                                rolesConverter())))
                // 쿠키 세션을 쓰지 않는 JSON API다. 브라우저가 아니라 BFF 서버가 부른다.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                // 관리자 API는 다른 서비스처럼 토큰이 없으면 401, 그 밖의 막힌 경로는 403이다.
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .defaultAuthenticationEntryPointFor(
                                                new BearerTokenAuthenticationEntryPoint(),
                                                PathPatternRequestMatcher.withDefaults()
                                                        .matcher("/v1/admin/**"))
                                        .defaultAuthenticationEntryPointFor(
                                                (request, response, e) ->
                                                        response.sendError(
                                                                HttpStatus.FORBIDDEN.value()),
                                                AnyRequestMatcher.INSTANCE));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain defaultSecurityFilterChain(
            HttpSecurity http,
            LoginRateLimitFilter loginRateLimitFilter,
            AccountRepository accounts) {
        http.addFilterAfter(
                        new DeactivatedAccountSessionFilter(accounts),
                        SecurityContextHolderFilter.class)
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        // actuator는 관리 포트(9001)에만 있고 호스트에 열지 않는다.
                                        .requestMatchers(EndpointRequest.toAnyEndpoint())
                                        .permitAll()
                                        .requestMatchers(
                                                "/error", "/v3/api-docs", "/v3/api-docs/**")
                                        .permitAll()
                                        // formLogin().permitAll()은 쿼리 문자열까지 정확히 비교해서 /login?locked
                                        // 같은
                                        // 안내 주소를 막는다. 경로만 보고 연다.
                                        .requestMatchers(HttpMethod.GET, "/login")
                                        .permitAll()
                                        // 2단계 화면은 비밀번호를 확인한 관리자 세션만 연다.
                                        .requestMatchers(
                                                SecondFactor.PAGE, SecondFactor.PAGE + "/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .formLogin(
                        form ->
                                form.loginPage("/login")
                                        .failureHandler(new LoginFailureHandler())
                                        .permitAll())
                .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** ADMIN 역할 로그인은 {@link SecondFactor#AUTHORITY} 요소가 있어야 한다. 다른 로그인과 클라이언트 인증은 그대로다. */
    private static AuthorizationManagerFactory<RequestAuthorizationContext> secondFactor() {
        return AuthorizationManagerFactories.<RequestAuthorizationContext>multiFactor()
                .requireFactors(SecondFactor.AUTHORITY)
                .when(SecondFactor::isRequired)
                .build();
    }

    private static JwtAuthenticationConverter rolesConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
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

    /** 요청 하나가 연결 하나만 쓰도록 트랜잭션으로 감싼다({@link TransactionalAuthorizationService}). */
    @Bean
    OAuth2AuthorizationService authorizationService(
            JdbcOperations jdbc,
            RegisteredClientRepository clients,
            TransactionOperations transactions) {
        return new TransactionalAuthorizationService(
                new JdbcOAuth2AuthorizationService(jdbc, clients), transactions);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcOperations jdbc,
            RegisteredClientRepository clients,
            TransactionOperations transactions) {
        return new TransactionalAuthorizationConsentService(
                new JdbcOAuth2AuthorizationConsentService(jdbc, clients), transactions);
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
    UserDetailsService userDetailsService(AccountRepository accounts, Clock clock) {
        return new AccountUserDetailsService(accounts, clock);
    }

    @Bean
    LoginRateLimitFilter loginRateLimitFilter(
            StringRedisTemplate redis, AuthProperties properties, Clock clock) {
        return new LoginRateLimitFilter(redis, properties.loginRateLimit().perMinute(), clock);
    }

    /** 필터는 보안 체인 안에서만 돈다. 서블릿 필터로 한 번 더 등록되지 않게 막는다. */
    @Bean
    FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilterRegistration(
            LoginRateLimitFilter filter) {
        FilterRegistrationBean<LoginRateLimitFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecondFactorSessionPort secondFactorSession(Clock clock) {
        return new SpringSecuritySecondFactorSessionAdapter(clock);
    }

    @Bean
    LoginAttemptRecorder loginAttemptRecorder(
            RecordLoginFailureUseCase failure, RecordLoginSuccessUseCase success) {
        return new LoginAttemptRecorder(failure, success);
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
