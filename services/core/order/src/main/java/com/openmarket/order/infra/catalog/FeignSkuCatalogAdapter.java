package com.openmarket.order.infra.catalog;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import com.openmarket.order.command.application.SkuCatalogPort;
import feign.FeignException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link SkuCatalogPort}의 OpenFeign 구현. catalog를 부를 수 없으면 503 {@code ORDER_CATALOG_UNAVAILABLE}이다.
 */
@Component
class FeignSkuCatalogAdapter implements SkuCatalogPort {

    private static final Logger log = LoggerFactory.getLogger(FeignSkuCatalogAdapter.class);

    private final CatalogSkuClient client;

    FeignSkuCatalogAdapter(CatalogSkuClient client) {
        this.client = client;
    }

    @Override
    public List<CatalogSku> findAll(Collection<UUID> skuIds) {
        List<CatalogSkuClient.SkuResponse> skus;
        try {
            skus = client.skus(skuIds);
        } catch (RuntimeException e) {
            throw unavailable(e);
        }
        return skus.stream()
                .map(sku -> new CatalogSku(sku.skuId(), "ON_SALE".equals(sku.productStatus())))
                .toList();
    }

    /**
     * 서킷 브레이커가 감싼 예외를 503으로 바꾼다. 4xx는 order가 요청을 잘못 만든 것이라 그대로 오류(500)로 둔다.
     *
     * <p>{@code CatalogSkuQueryDao}에 같은 규칙이 있다. 커맨드와 조회가 Feign 클라이언트를 따로 두기 때문이다.
     */
    static RuntimeException unavailable(RuntimeException e) {
        Throwable root = e;
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof FeignException.FeignClientException clientError) {
                return new IllegalStateException("catalog가 요청을 거절했다: " + clientError.status(), e);
            }
            root = cause;
        }
        // 서킷 브레이커가 감싼 예외(NoFallbackAvailableException)보다 맨 안쪽 원인이 쓸모 있다.
        log.warn("catalog를 부를 수 없다: {}", root.toString());
        return new BusinessException(
                ErrorType.UNAVAILABLE,
                "ORDER_CATALOG_UNAVAILABLE",
                "상품 정보를 잠시 불러올 수 없습니다. 잠시 뒤 다시 시도해 주세요.",
                e);
    }
}
