package com.openmarket.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.http.HttpMethod;

/**
 * 서비스 리소스 서버 설정. 값은 infra/config-repo의 {@code <service>.yml}에 둔다.
 *
 * @param publicPaths 토큰 없이 통과시킬 경로. 판매자·관리자 경로와 내부 경로는 여기에 넣어도 공개되지 않는다. gateway의 {@code
 *     openmarket.gateway.public-paths}에도 같은 경로를 gateway 기준으로 적어야 바깥에서 열린다
 */
@ConfigurationProperties("openmarket.security")
public record ResourceServerSecurityProperties(@DefaultValue List<PublicPath> publicPaths) {

    /**
     * 토큰 없이 통과시킬 경로 하나.
     *
     * @param method HTTP 메서드
     * @param pattern 서비스 기준 경로 패턴. 예: {@code /v1/products/**}
     */
    public record PublicPath(HttpMethod method, String pattern) {}
}
