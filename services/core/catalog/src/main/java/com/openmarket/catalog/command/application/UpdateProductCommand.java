package com.openmarket.catalog.command.application;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 상품을 고친다(CAT-2). 기존 SKU는 모두 보내야 한다.
 *
 * @param imageFileIds 바뀐 뒤의 이미지 전체. 순서대로
 */
public record UpdateProductCommand(
        UUID sellerId,
        UUID productId,
        UUID categoryId,
        String name,
        String description,
        List<UUID> imageFileIds,
        List<Sku> skus) {

    /**
     * 옵션 조합 한 줄.
     *
     * @param skuId 기존 SKU면 그 ID, 새 SKU면 null
     * @param initialStock 새 SKU의 초기 재고. 기존 SKU면 무시한다(재고는 재고 조정으로 바꾼다)
     */
    public record Sku(
            @Nullable UUID skuId, List<String> optionValues, long price, int initialStock) {}
}
