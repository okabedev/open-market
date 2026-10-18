package com.openmarket.member;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** MockMvc 요청에 넣는 인증. 서명·iss·aud 검증과 roles 변환은 common-security 테스트가 확인한다. */
final class MemberTokens {

    private MemberTokens() {}

    static RequestPostProcessor buyer(UUID memberId) {
        return jwt().jwt(
                        token ->
                                token.subject(memberId.toString()).claim("roles", List.of("BUYER")))
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    }

    static RequestPostProcessor seller(UUID memberId) {
        return jwt().jwt(
                        token ->
                                token.subject(memberId.toString())
                                        .claim("roles", List.of("BUYER", "SELLER")))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_BUYER"),
                        new SimpleGrantedAuthority("ROLE_SELLER"));
    }

    static RequestPostProcessor admin(UUID adminId) {
        return jwt().jwt(
                        token -> token.subject(adminId.toString()).claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    /** 서비스 간 토큰. 역할이 없다. */
    static RequestPostProcessor internal() {
        return jwt().jwt(token -> token.subject("notification").claim("scope", "internal"))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal"));
    }
}
