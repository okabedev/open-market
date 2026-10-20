package com.openmarket.catalog.query.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 구매자 상품 상세. 판매 중이거나 판매 중지된 상품만 보인다.
 *
 * @param categoryPath 대분류부터 상품 카테고리까지
 * @param imageFileIds 순서대로. 이미지는 {@code /files/{fileId}/w300}, {@code /files/{fileId}/w800}으로 받는다
 * @param optionNames 옵션 이름. SKU의 옵션 값이 이 순서다
 * @param inStock 판매 가능한 SKU가 하나라도 있는가
 */
public record ProductDetailView(
        UUID productId,
        ProductStatus status,
        String name,
        String description,
        List<CategoryRefView> categoryPath,
        UUID sellerId,
        String storeName,
        List<UUID> imageFileIds,
        List<String> optionNames,
        List<Sku> skus,
        long minPrice,
        boolean inStock,
        @Nullable Instant publishedAt) {

    /**
     * 옵션 조합 하나. 남은 수량은 보이지 않는다.
     *
     * @param inStock 판매 가능 수량이 있는가
     */
    @Schema(name = "ProductDetailSkuView")
    public record Sku(UUID skuId, List<String> optionValues, long price, boolean inStock) {}
}
