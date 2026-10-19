package com.openmarket.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** MockMvc 요청에 넣는 인증. 서명·iss·aud 검증과 roles 변환은 common-security 테스트가 확인한다. */
final class CatalogTokens {

    private CatalogTokens() {}

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
}
