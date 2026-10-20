package com.openmarket.order.query.dao;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import feign.FeignException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 장바구니 화면에 쓰는 catalog SKU 정보(docs/architecture/order.md "catalog 조회"). catalog를 부를 수 없으면 503 {@code
 * ORDER_CATALOG_UNAVAILABLE}이다. 커맨드 쪽 클라이언트({@code infra.catalog})와 모델을 공유하지 않으려고 따로 둔다.
 */
@Repository
public class CatalogSkuQueryDao {

    private static final Logger log = LoggerFactory.getLogger(CatalogSkuQueryDao.class);

    private final Client client;

    public CatalogSkuQueryDao(Client client) {
        this.client = client;
    }

    /** 없는 SKU는 결과에서 빠진다. */
    public List<Sku> findAll(Collection<UUID> skuIds) {
        if (skuIds.isEmpty()) {
            return List.of();
        }
        try {
            return client.skus(skuIds);
        } catch (RuntimeException e) {
            throw unavailable(e);
        }
    }

    /** 4xx는 order가 요청을 잘못 만든 것이라 오류(500)로 두고, 나머지(타임아웃, 5xx, 서킷 열림)는 503이다. */
    private static RuntimeException unavailable(RuntimeException e) {
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

    /** catalog 내부 API. 설정은 커맨드 쪽 클라이언트와 같다(config-repo order.yml). */
    @FeignClient(
            name = "catalog",
            contextId = "catalogSkuQuery",
            url = "${openmarket.order.catalog-url:}")
    public interface Client {

        @GetMapping("/internal/v1/skus")
        List<Sku> skus(@RequestParam("ids") Collection<UUID> ids);
    }

    /** catalog 응답 중 장바구니 화면이 쓰는 필드. */
    public record Sku(
            UUID skuId,
            UUID productId,
            String productName,
            String productStatus,
            UUID sellerId,
            String storeName,
            List<String> optionNames,
            List<String> optionValues,
            long price,
            int availableQuantity,
            @Nullable UUID thumbnailFileId) {}
}
