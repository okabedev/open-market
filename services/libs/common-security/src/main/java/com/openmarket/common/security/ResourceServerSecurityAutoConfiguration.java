package com.openmarket.common.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 서비스의 리소스 서버 설정(docs/architecture/auth.md "인가" 2). gateway를 우회한 호출도 막도록 서비스가 토큰을 한 번 더 검증한다.
 *
 * <p>서명·{@code iss}·{@code aud}·{@code exp} 검증은 config-repo 공통 {@code application.yml}의 {@code
 * spring.security.oauth2.resourceserver.jwt.*}로 Boot가 한다. 여기서는 경로 규칙과 권한 변환만 정한다. 규칙은 위에서부터 먼저 맞는
 * 것이 이긴다.
 *
 * <ol>
 *   <li>actuator(health, info, prometheus), OpenAPI 문서, 오류 응답은 연다. gateway는 이 경로를 넘기지 않는다
 *   <li>{@code /internal/**}는 {@code internal} scope(서비스 간 토큰)가 있어야 한다
 *   <li>{@code /v1/seller/**}는 {@code SELLER}, {@code /v1/admin/**}는 {@code ADMIN} 역할이 있어야 한다
 *   <li>{@code openmarket.security.public-paths}에 적은 경로는 토큰 없이 연다. 토큰이 있으면 검증하고 주체로 쓴다
 *   <li>나머지 {@code /v1/**}는 사용자 토큰(역할이 하나라도 있는 토큰)이어야 한다. 서비스 간 토큰은 역할이 없어 막힌다
 *   <li>그 밖의 경로는 막는다
 * </ol>
 *
 * <p>서비스가 {@link SecurityFilterChain}을 직접 만들면 이 설정은 빠진다.
 */
@AutoConfiguration(
        beforeName = {
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
            "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration",
            "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration"
        })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ResourceServerSecurityProperties.class)
public class ResourceServerSecurityAutoConfiguration {

    /**
     * 사용자 토큰의 {@code roles}는 {@code ROLE_<역할>}로, 서비스 간 토큰의 {@code scope}는 {@code SCOPE_<scope>}로
     * 바꾼다.
     */
    @Bean
    @ConditionalOnMissingBean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> {
                    Collection<GrantedAuthority> authorities = new ArrayList<>(roles.convert(jwt));
                    authorities.addAll(scopes.convert(jwt));
                    return List.copyOf(authorities);
                });
        return converter;
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain resourceServerSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            ResourceServerSecurityProperties properties) {
        http.authorizeHttpRequests(
                        authorize -> {
                            authorize
                                    .requestMatchers(
                                            "/actuator/health",
                                            "/actuator/health/**",
                                            "/actuator/info",
                                            "/actuator/prometheus",
                                            "/v3/api-docs",
                                            "/v3/api-docs/**",
                                            "/error")
                                    .permitAll()
                                    .requestMatchers("/internal/**")
                                    .hasAuthority("SCOPE_internal")
                                    .requestMatchers("/v1/seller/**")
                                    .hasRole("SELLER")
                                    .requestMatchers("/v1/admin/**")
                                    .hasRole("ADMIN");
                            properties
                                    .publicPaths()
                                    .forEach(
                                            path ->
                                                    authorize
                                                            .requestMatchers(
                                                                    path.method(), path.pattern())
                                                            .permitAll());
                            authorize
                                    .requestMatchers("/v1/**")
                                    .hasAnyRole("BUYER", "SELLER", "ADMIN")
                                    .anyRequest()
                                    .denyAll();
                        })
                // 브라우저가 아니라 BFF와 서비스가 Bearer 토큰으로 부른다. 쿠키 세션을 쓰지 않는다.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .oauth2ResourceServer(
                        server ->
                                server.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter)));
        return http.build();
    }
}
