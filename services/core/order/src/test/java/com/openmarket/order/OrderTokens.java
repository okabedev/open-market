package com.openmarket.order;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** MockMvc 요청에 넣는 인증. 서명·iss·aud 검증과 roles 변환은 common-security 테스트가 확인한다. */
final class OrderTokens {

    private OrderTokens() {}

    static RequestPostProcessor buyer(UUID memberId) {
        return jwt().jwt(
                        token ->
                                token.subject(memberId.toString()).claim("roles", List.of("BUYER")))
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    }

    /** 서비스 간 토큰. 역할 없이 {@code internal} scope만 있다. */
    static RequestPostProcessor internal() {
        return jwt().jwt(token -> token.subject("member").claim("scope", "internal"))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal"));
    }

    static RequestPostProcessor seller(UUID sellerId) {
        return jwt().jwt(
                        token ->
                                token.subject(sellerId.toString())
                                        .claim("roles", List.of("BUYER", "SELLER")))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_BUYER"),
                        new SimpleGrantedAuthority("ROLE_SELLER"));
    }
}
