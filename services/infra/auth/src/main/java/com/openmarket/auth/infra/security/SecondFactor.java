package com.openmarket.auth.infra.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * 관리자 2단계 인증 요소(docs/architecture/auth.md "관리자 2단계 인증", ADR 0024). Spring Security 7 다중 요소 인증의 요소
 * 권한으로 나타낸다. 비밀번호 로그인은 {@code FACTOR_PASSWORD}를, TOTP 확인은 {@value #AUTHORITY}를 더한다.
 */
final class SecondFactor {

    static final String AUTHORITY = "FACTOR_TOTP";

    /** 2단계 화면 주소. 요소가 없는 관리자를 여기로 보낸다. */
    static final String PAGE = "/login/totp";

    private SecondFactor() {}

    /** ADMIN 역할이 있는 로그인이면 어느 클라이언트로 로그인하든 2단계 인증이 필요하다. */
    static boolean isRequired(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
