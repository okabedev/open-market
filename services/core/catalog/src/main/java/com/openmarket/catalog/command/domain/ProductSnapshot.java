package com.openmarket.catalog.command.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 상품 스냅샷. {@code ProductRegistered}, {@code ProductPublished}, {@code ProductChanged}의 페이로드다
 * (docs/architecture/context-map.md "catalog.product"). 이벤트 이름은 아래 상수다.
 *
 * @param revision 이 이벤트를 낼 때 올린 상품 리비전. 소비자는 받은 것보다 작거나 같은 값을 무시한다
 * @param status 상품 상태 이름
 * @param categoryPath 대분류부터 상품 카테고리까지의 ID
 * @param minPrice SKU 가격 중 가장 낮은 값(원)
 * @param inStock 판매 가능한 SKU가 하나라도 있는가
 * @param publishedAt 판매를 시작한 시각. 시작한 적이 없으면 null
 */
public record ProductSnapshot(
        UUID productId,
        long revision,
        String status,
        UUID sellerId,
        String storeName,
        UUID categoryId,
        List<UUID> categoryPath,
        String name,
        String description,
        List<UUID> imageFileIds,
        List<String> optionNames,
        List<SkuSnapshot> skus,
        long minPrice,
        boolean inStock,
        @Nullable Instant publishedAt) {

    public static final String REGISTERED = "ProductRegistered";
    public static final String PUBLISHED = "ProductPublished";
    public static final String CHANGED = "ProductChanged";

    /**
     * SKU 하나.
     *
     * @param inStock 판매 가능 수량이 있는가. 수량 자체는 내보내지 않는다
     */
    public record SkuSnapshot(UUID skuId, List<String> optionValues, long price, boolean inStock) {}
}
