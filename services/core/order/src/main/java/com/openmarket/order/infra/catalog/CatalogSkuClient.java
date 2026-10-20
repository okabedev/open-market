package com.openmarket.order.infra.catalog;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * catalog 내부 API(docs/architecture/catalog.md "내부 API"). Eureka로 찾고, 서비스 간 토큰과 서킷 브레이커가
 * 붙는다(config-repo order.yml). {@code openmarket.order.catalog-url}이 있으면 그 주소로 바로 부른다(테스트).
 */
@FeignClient(
        name = "catalog",
        contextId = "catalogSkuCommand",
        url = "${openmarket.order.catalog-url:}")
interface CatalogSkuClient {

    @GetMapping("/internal/v1/skus")
    List<SkuResponse> skus(@RequestParam("ids") Collection<UUID> ids);

    /** 응답에서 커맨드가 쓰는 필드만 읽는다. 대표 이미지는 쓰지 않는다. */
    record SkuResponse(
            UUID skuId,
            UUID productId,
            String productName,
            String productStatus,
            UUID sellerId,
            String storeName,
            UUID categoryId,
            List<String> optionNames,
            List<String> optionValues,
            long price,
            int availableQuantity) {}
}
