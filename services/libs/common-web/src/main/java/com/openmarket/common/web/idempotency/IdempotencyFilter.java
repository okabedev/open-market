package com.openmarket.common.web.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * {@code Idempotency-Key} 헤더가 있는 요청의 본문과 응답을 버퍼에 담는다. 실제 판단은 {@link IdempotencyInterceptor}가 한다.
 *
 * <p>본문이 {@link #MAX_BODY_BYTES}를 넘으면 버퍼에 담지 않는다. 그 요청은 인터셉터가 거절한다.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    static final int MAX_BODY_BYTES = 1024 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(IdempotencyInterceptor.HEADER) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            chain.doFilter(request, response);
            return;
        }
        byte[] body;
        try (InputStream input = request.getInputStream()) {
            body = input.readNBytes(MAX_BODY_BYTES + 1);
        }
        if (body.length > MAX_BODY_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(new CachedBodyHttpServletRequest(request, body), cachingResponse);
        } finally {
            cachingResponse.copyBodyToResponse();
        }
    }
}
