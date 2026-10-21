package com.openmarket.payment.infra.toss;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스 API 설정(infra/config-repo/payment.yml {@code openmarket.payment.toss}).
 *
 * @param baseUrl 토스 API 주소. E2E는 가짜 PG 주소다
 * @param secretKey 시크릿 키. payment만 갖는다
 */
@ConfigurationProperties("openmarket.payment.toss")
public record TossProperties(
        URI baseUrl, String secretKey, Duration connectTimeout, Duration readTimeout) {

    @Override
    public String toString() {
        return "TossProperties[baseUrl=" + baseUrl + ", secretKey=***]";
    }
}
