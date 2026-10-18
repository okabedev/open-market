package com.openmarket.auth.infra.security;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로그인 세션의 계정이 비활성화됐으면(탈퇴, ACC-4) 세션을 끝낸다. 그러지 않으면 브라우저에 남은 auth 세션으로 인가 코드를 다시 받을 수 있다. 세션이 끝난 요청은
 * 익명으로 이어져 로그인 화면으로 간다.
 *
 * <p>폼 로그인한 세션({@link UsernamePasswordAuthenticationToken})만 본다. 클라이언트 인증(토큰 엔드포인트)은 상관없다.
 */
class DeactivatedAccountSessionFilter extends OncePerRequestFilter {

    private final AccountRepository accounts;

    DeactivatedAccountSessionFilter(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof UsernamePasswordAuthenticationToken login
                && login.isAuthenticated()
                && !isActive(login.getName())) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isActive(String accountId) {
        try {
            return accounts.findById(UUID.fromString(accountId))
                    .filter(Account::isActive)
                    .isPresent();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
