package com.openmarket.order.command.application;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * catalog의 SKU 조회(docs/architecture/order.md "catalog 조회"). catalog를 부를 수 없으면 503 {@code
 * ORDER_CATALOG_UNAVAILABLE}을 던진다.
 */
public interface SkuCatalogPort {

    /** 없는 SKU는 결과에서 빠진다. */
    List<CatalogSku> findAll(Collection<UUID> skuIds);

    /**
     * 커맨드가 판단에 쓰는 SKU 정보.
     *
     * @param onSale 상품이 판매 중({@code ON_SALE})인가
     */
    record CatalogSku(UUID skuId, boolean onSale) {}
}
