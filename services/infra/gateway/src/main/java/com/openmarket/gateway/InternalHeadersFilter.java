package com.openmarket.gateway;

import java.util.List;
import java.util.Locale;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * 서비스로 넘기는 요청에서 {@code X-Internal-*} 헤더를 지운다(docs/architecture/auth.md "인가" 1). 이 헤더는 서비스 사이에서만 쓰므로
 * 바깥에서 들어온 값은 위조로 본다.
 *
 * <p>라우트 필터가 아니라 프록시 직전에 모든 라우트에 적용되는 {@link HttpHeadersFilter}로 둔다. 라우트를 추가해도 빠뜨릴 수 없다.
 */
@Component
class InternalHeadersFilter implements HttpHeadersFilter {

    static final String PREFIX = "x-internal-";

    @Override
    public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
        HttpHeaders filtered = new HttpHeaders();
        for (String name : input.headerNames()) {
            List<String> values = input.get(name);
            if (values != null && !name.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
                filtered.addAll(name, values);
            }
        }
        return filtered;
    }

    @Override
    public boolean supports(Type type) {
        return type == Type.REQUEST;
    }
}
