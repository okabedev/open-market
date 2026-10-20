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
     * 커맨드가 판단하고 주문 항목 스냅샷에 쓰는 SKU 정보.
     *
     * @param onSale 상품이 판매 중({@code ON_SALE})인가
     * @param optionNames 상품의 옵션 이름. {@code optionValues}가 이 순서다
     * @param price 지금 개당 가격(원)
     * @param availableQuantity 판매 가능 수량
     */
    record CatalogSku(
            UUID skuId,
            UUID productId,
            String productName,
            boolean onSale,
            UUID sellerId,
            String storeName,
            UUID categoryId,
            List<String> optionNames,
            List<String> optionValues,
            long price,
            int availableQuantity) {}
}
