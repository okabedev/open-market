package com.openmarket.common.security.testapp;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 경로 접두어마다 하나씩. 응답은 토큰의 {@code sub}다. */
@RestController
class SecuredTestController {

    @GetMapping({"/v1/me", "/v1/seller/me", "/v1/admin/me", "/internal/v1/me", "/other/me"})
    String me(@AuthenticationPrincipal Jwt jwt) {
        return jwt.getSubject();
    }
}
