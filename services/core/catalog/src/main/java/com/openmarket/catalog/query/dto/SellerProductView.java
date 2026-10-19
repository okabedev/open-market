package com.openmarket.catalog.query.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 판매자 본인 상품 상세. 수정 화면이 쓴다. 재고 수량을 그대로 보여 준다.
 *
 * @param categoryPath 대분류부터 상품 카테고리까지
 * @param imageFileIds 순서대로
 */
public record SellerProductView(
        UUID productId,
        ProductStatus status,
        String name,
        String description,
        List<CategoryRefView> categoryPath,
        List<UUID> imageFileIds,
        List<String> optionNames,
        List<Sku> skus,
        @Nullable Instant publishedAt,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 옵션 조합 하나와 재고.
     *
     * @param onHand 실재고
     * @param reserved 주문으로 예약된 수량
     * @param available 판매 가능 수량({@code onHand - reserved})
     */
    public record Sku(
            UUID skuId,
            List<String> optionValues,
            long price,
            int onHand,
            int reserved,
            int available) {}
}
