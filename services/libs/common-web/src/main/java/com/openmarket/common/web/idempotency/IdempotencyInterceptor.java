package com.openmarket.common.web.idempotency;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

/** {@link Idempotent}가 붙은 핸들러의 중복 요청을 걸러 낸다. 요청·응답 버퍼는 {@link IdempotencyFilter}가 만든다. */
public class IdempotencyInterceptor implements HandlerInterceptor {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private static final Logger log = LoggerFactory.getLogger(IdempotencyInterceptor.class);
    private static final String CONTEXT_ATTRIBUTE =
            IdempotencyInterceptor.class.getName() + ".context";
    private static final int MAX_KEY_LENGTH = 100;
    private static final String ANONYMOUS = "anonymous";

    private final IdempotencyStore store;

    public IdempotencyInterceptor(IdempotencyStore store) {
        this.store = store;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!(handler instanceof HandlerMethod method)
                || !method.hasMethodAnnotation(Idempotent.class)) {
            return true;
        }
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            throw new BusinessException(
                    ErrorType.INVALID,
                    "COMMON_IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key 헤더가 필요합니다.");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new BusinessException(
                    ErrorType.INVALID,
                    "COMMON_IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key는 100자 이하여야 합니다.");
        }
        CachedBodyHttpServletRequest cached =
                WebUtils.getNativeRequest(request, CachedBodyHttpServletRequest.class);
        if (cached == null) {
            throw new BusinessException(
                    ErrorType.INVALID, "COMMON_IDEMPOTENCY_BODY_TOO_LARGE", "요청 본문이 너무 큽니다.");
        }
        String principal = principalOf(request);
        String hash = hash(request.getMethod(), request.getRequestURI(), cached.getBody());

        return switch (store.begin(principal, key, hash)) {
            case IdempotencyStore.Started _ -> {
                request.setAttribute(CONTEXT_ATTRIBUTE, new Context(principal, key));
                yield true;
            }
            case IdempotencyStore.Completed completed -> {
                replay(response, completed);
                yield false;
            }
            case IdempotencyStore.InProgress _ ->
                    throw new BusinessException(
                            ErrorType.CONFLICT,
                            "COMMON_IDEMPOTENCY_IN_PROGRESS",
                            "같은 요청을 처리하고 있습니다.");
            case IdempotencyStore.Mismatch _ ->
                    throw new BusinessException(
                            ErrorType.UNPROCESSABLE,
                            "COMMON_IDEMPOTENCY_KEY_REUSED",
                            "같은 Idempotency-Key로 다른 요청을 보냈습니다.");
        };
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            @Nullable Exception exception) {
        if (!(request.getAttribute(CONTEXT_ATTRIBUTE) instanceof Context context)) {
            return;
        }
        ContentCachingResponseWrapper cached =
                WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
        int status = response.getStatus();
        if (exception != null || status >= 500 || cached == null) {
            store.release(context.principal(), context.key());
            return;
        }
        try {
            store.complete(context.principal(), context.key(), status, jsonBodyOf(cached));
        } catch (RuntimeException e) {
            log.warn("멱등성 응답을 저장하지 못해 기록을 지운다: key={}", context.key(), e);
            store.release(context.principal(), context.key());
        }
    }

    private static void replay(HttpServletResponse response, IdempotencyStore.Completed completed)
            throws IOException {
        response.setStatus(completed.status());
        response.setHeader(REPLAYED_HEADER, "true");
        String body = completed.jsonBody();
        if (body != null) {
            response.setContentType(
                    completed.status() >= 400
                            ? MediaType.APPLICATION_PROBLEM_JSON_VALUE
                            : MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(body);
        }
    }

    private static @Nullable String jsonBodyOf(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        String contentType = response.getContentType();
        if (content.length == 0 || contentType == null || !contentType.contains("json")) {
            return null;
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private static String principalOf(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        return principal == null ? ANONYMOUS : principal.getName();
    }

    private static String hash(String method, String uri, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((method + " " + uri + "\n").getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없다", e);
        }
    }

    private record Context(String principal, String key) {}
}
