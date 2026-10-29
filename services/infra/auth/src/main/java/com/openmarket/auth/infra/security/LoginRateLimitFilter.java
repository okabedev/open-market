package com.openmarket.auth.infra.security;

import com.openmarket.auth.command.application.SecurityEventPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로그인 시도(POST /login)와 2단계 확인(POST /login/totp, /login/totp/enroll)을 IP 단위로 한 카운터로 제한한다(NFR-5). 로그인
 * 화면은 브라우저가 auth에 직접 오므로 gateway의 레이트 리밋을 거치지 않는다(docs/architecture/auth.md "보안 기본값").
 *
 * <p>1분 고정 창 카운터({@code auth:login-rate:<ip>:<분>})를 Redis에 둔다. 한도를 넘으면 인증을 하지 않고 안내 화면으로 보내므로 계정 잠금
 * 횟수도 늘지 않는다. Redis가 응답하지 않으면 통과시킨다(fail-open). 명령 타임아웃(200ms)으로 요청이 멈추지 않게 한다.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);
    private static final Duration WINDOW = Duration.ofMinutes(1);
    // 폼 로그인과 MVC처럼 디코딩한 경로로 맞춘다. 원래 URI로 비교하면 /%6Cogin이 한도를 피해 로그인된다.
    private static final RequestMatcher SECOND_FACTOR =
            new OrRequestMatcher(post("/login/totp"), post("/login/totp/enroll"));
    private static final RequestMatcher LIMITED =
            new OrRequestMatcher(post("/login"), SECOND_FACTOR);

    private final StringRedisTemplate redis;
    private final int perMinute;
    private final Clock clock;
    private final SecurityEventPort securityEvents;

    public LoginRateLimitFilter(
            StringRedisTemplate redis,
            int perMinute,
            Clock clock,
            SecurityEventPort securityEvents) {
        this.redis = redis;
        this.perMinute = perMinute;
        this.clock = clock;
        this.securityEvents = securityEvents;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LIMITED.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (exceeded(request.getRemoteAddr())) {
            response.setStatus(HttpServletResponse.SC_SEE_OTHER);
            String page = SECOND_FACTOR.matches(request) ? "/login/totp" : "/login";
            response.setHeader("Location", request.getContextPath() + page + "?limited");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean exceeded(String ip) {
        String key = "auth:login-rate:" + ip + ":" + clock.millis() / WINDOW.toMillis();
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, WINDOW);
            }
            if (count != null && count == perMinute + 1) {
                securityEvents.loginRateLimited(ip);
            }
            return count != null && count > perMinute;
        } catch (DataAccessException e) {
            log.warn("로그인 레이트 리밋을 확인하지 못해 통과시킨다: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    private static RequestMatcher post(String path) {
        return PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, path);
    }
}
