package com.openmarket.order;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.test.context.DynamicPropertyRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * catalog 내부 API와 auth 토큰 엔드포인트를 흉내 내는 WireMock 서버. JVM에서 하나만 띄워 테스트 클래스들이 같이 쓴다. 테스트마다 {@link
 * #reset()}으로 스텁을 지운다.
 */
final class CatalogStub {

    static final WireMockServer SERVER = new WireMockServer(options().dynamicPort());

    static final String SKUS = "/internal/v1/skus";
    static final String TOKEN = "/oauth2/token";
    static final String ACCESS_TOKEN = "service-token";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    static {
        SERVER.start();
    }

    private CatalogStub() {}

    /** Feign 클라이언트와 토큰 요청이 이 서버로 가게 한다. */
    static void register(DynamicPropertyRegistry registry) {
        registry.add("openmarket.order.catalog-url", SERVER::baseUrl);
        registry.add(
                "spring.security.oauth2.client.provider.auth.token-uri",
                () -> SERVER.baseUrl() + TOKEN);
    }

    /** 스텁과 요청 기록을 지우고 토큰 엔드포인트만 다시 둔다. */
    static void reset() {
        SERVER.resetAll();
        SERVER.stubFor(
                post(urlPathEqualTo(TOKEN))
                        .willReturn(
                                okJson(
                                        JSON.writeValueAsString(
                                                Map.of(
                                                        "access_token",
                                                        ACCESS_TOKEN,
                                                        "token_type",
                                                        "Bearer",
                                                        "expires_in",
                                                        3600,
                                                        "scope",
                                                        "internal")))));
    }

    /** {@code GET /internal/v1/skus}가 주어진 SKU들을 준다(요청 ID와 상관없이). */
    static void skus(List<Map<String, @Nullable Object>> skus) {
        SERVER.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo(SKUS))
                        .willReturn(okJson(JSON.writeValueAsString(skus))));
    }

    static void skusFail(int status) {
        SERVER.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo(SKUS))
                        .willReturn(aResponse().withStatus(status)));
    }

    /** catalog 응답 한 줄. */
    static Map<String, @Nullable Object> sku(
            UUID skuId,
            UUID sellerId,
            String storeName,
            String productStatus,
            long price,
            int available) {
        Map<String, @Nullable Object> sku = new java.util.HashMap<>();
        sku.put("skuId", skuId.toString());
        sku.put("productId", UUID.randomUUID().toString());
        sku.put("productName", "반팔 티셔츠");
        sku.put("productStatus", productStatus);
        sku.put("sellerId", sellerId.toString());
        sku.put("storeName", storeName);
        sku.put("categoryId", "0199f000-0000-7000-8000-000000000101");
        sku.put("optionNames", List.of("색상"));
        sku.put("optionValues", List.of("블랙"));
        sku.put("price", price);
        sku.put("availableQuantity", available);
        sku.put("thumbnailFileId", null);
        return sku;
    }
}
