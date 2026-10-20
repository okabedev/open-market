package com.openmarket.order.command.application;

import com.openmarket.order.command.application.SkuCatalogPort.CatalogSku;
import java.util.List;
import java.util.UUID;

/** 유스케이스 테스트용 catalog SKU. */
final class CatalogSkus {

    static final UUID CATEGORY = UUID.fromString("0199f000-0000-7000-8000-000000000101");

    private CatalogSkus() {}

    static CatalogSku sku(UUID skuId, boolean onSale) {
        return sku(skuId, UUID.randomUUID(), "길동상회", onSale, 19_900, 10);
    }

    static CatalogSku sku(
            UUID skuId,
            UUID sellerId,
            String storeName,
            boolean onSale,
            long price,
            int availableQuantity) {
        return new CatalogSku(
                skuId,
                UUID.randomUUID(),
                "반팔 티셔츠",
                onSale,
                sellerId,
                storeName,
                CATEGORY,
                List.of("색상"),
                List.of("블랙"),
                price,
                availableQuantity);
    }
}
