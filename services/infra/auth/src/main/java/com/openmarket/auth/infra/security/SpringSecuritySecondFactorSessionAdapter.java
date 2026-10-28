package com.openmarket.auth.infra.security;

import com.openmarket.auth.command.application.SecondFactorSessionPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 2단계 인증을 마친 로그인 세션: 지금 인증에 {@link SecondFactor#AUTHORITY} 요소를 더해 세션에 저장한다. 세션 고정 공격을 막으려고 세션 ID를
 * 바꾼다. 돌아갈 주소는 인가 엔드포인트가 2단계 화면으로 보내며 저장한 요청이다(DelegatingMissingAuthorityAccessDeniedHandler).
 */
public class SpringSecuritySecondFactorSessionAdapter implements SecondFactorSessionPort {

    private final SecurityContextHolderStrategy contexts =
            SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();
    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final Clock clock;

    public SpringSecuritySecondFactorSessionAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String complete() {
        HttpServletRequest request = request();
        HttpServletResponse response = response();
        Authentication current = contexts.getContext().getAuthentication();
        Object principal = current == null ? null : current.getPrincipal();
        if (current == null || principal == null) {
            throw new IllegalStateException("로그인 세션이 없다");
        }
        List<GrantedAuthority> authorities = new ArrayList<>(current.getAuthorities());
        authorities.add(
                FactorGrantedAuthority.withAuthority(SecondFactor.AUTHORITY)
                        .issuedAt(clock.instant())
                        .build());
        UsernamePasswordAuthenticationToken completed =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        completed.setDetails(current.getDetails());

        request.changeSessionId();
        SecurityContext context = contexts.createEmptyContext();
        context.setAuthentication(completed);
        contexts.setContext(context);
        repository.saveContext(context, request, response);

        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved == null) {
            return "/";
        }
        requestCache.removeRequest(request, response);
        return saved.getRedirectUrl();
    }

    @Override
    public void end() {
        new SecurityContextLogoutHandler()
                .logout(request(), response(), contexts.getContext().getAuthentication());
    }

    private static HttpServletRequest request() {
        return attributes().getRequest();
    }

    private static HttpServletResponse response() {
        HttpServletResponse response = attributes().getResponse();
        if (response == null) {
            throw new IllegalStateException("응답이 없다");
        }
        return response;
    }

    private static ServletRequestAttributes attributes() {
        return (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    }
}
