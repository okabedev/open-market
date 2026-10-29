package com.openmarket.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

/**
 * 리소스 서버 설정(docs/architecture/auth.md "인가" 1). 토큰 서명·{@code iss}·{@code aud}·{@code exp} 검증과 {@code
 * roles} 클레임의 권한 변환은 config-repo의 {@code spring.security.oauth2.resourceserver.jwt.*}가 정한다.
 *
 * <p>규칙은 위에서부터 먼저 맞는 것이 이긴다. 내부 경로와 서비스 actuator는 막고, 역할 경로는 역할을 보고, 공개 경로는 통과시키고, 나머지는 로그인이 필요하다.
 * 세션을 만들지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
class SecurityConfiguration {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http, GatewayProperties properties) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(
                        exchanges -> {
                            // 관리 포트(8081)에도 이 체인이 적용된다. actuator는 외부 포트에 매핑되지 않으므로 여기서는 관리 포트만 열린다.
                            exchanges
                                    .pathMatchers(
                                            "/actuator/health",
                                            "/actuator/health/**",
                                            "/actuator/info",
                                            "/actuator/prometheus")
                                    .permitAll();
                            exchanges
                                    // OpenAPI 문서는 프론트 타입 생성용이다. 서비스 테스트가 직접 읽고, 바깥에는 내주지 않는다.
                                    .pathMatchers(
                                            "/api/*/internal/**",
                                            "/api/*/actuator/**",
                                            "/api/*/v3/api-docs",
                                            "/api/*/v3/api-docs/**")
                                    .denyAll();
                            exchanges.pathMatchers("/api/*/v1/seller/**").hasRole("SELLER");
                            exchanges.pathMatchers("/api/*/v1/admin/**").hasRole("ADMIN");
                            properties
                                    .publicPaths()
                                    .forEach(
                                            path ->
                                                    exchanges
                                                            .pathMatchers(
                                                                    path.method(), path.pattern())
                                                            .permitAll());
                            exchanges.anyExchange().authenticated();
                        })
                .oauth2ResourceServer(server -> server.jwt(Customizer.withDefaults()))
                .build();
    }
}
