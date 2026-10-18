package com.openmarket.auth.infra.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/** 잠긴 계정은 {@code /login?locked}, 나머지 실패는 모두 {@code /login?error}로 보낸다. 계정이 있는지는 알려 주지 않는다. */
public class LoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        String target = e instanceof LockedException ? "/login?locked" : "/login?error";
        response.sendRedirect(request.getContextPath() + target);
    }
}
