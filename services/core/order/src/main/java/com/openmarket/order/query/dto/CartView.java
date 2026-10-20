package com.openmarket.order.query.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 장바구니. 판매자별로 묶는다(ORD-1). 묶음은 가장 최근에 담은 줄이 있는 판매자부터, 묶음 안의 줄은 최근에 담은 순이다. 상품 정보는 지금 catalog 값이다.
 *
 * @param missingSkuIds catalog에 없는 SKU. 묶음에 넣지 않는다
 */
public record CartView(List<SellerGroup> sellers, List<UUID> missingSkuIds) {

    /** 한 판매자의 줄들. */
    @Schema(name = "CartSellerGroupView")
    public record SellerGroup(UUID sellerId, String storeName, List<Item> items) {}

    /**
     * 장바구니 줄 하나.
     *
     * @param optionNames 옵션 이름. {@code optionValues}가 이 순서다
     * @param price 지금 가격(원). 주문할 때 다시 확인한다
     * @param thumbnailFileId 상품 대표 이미지. {@code /files/{fileId}/w300}으로 받는다
     */
    @Schema(name = "CartItemView")
    public record Item(
            UUID skuId,
            UUID productId,
            String productName,
            List<String> optionNames,
            List<String> optionValues,
            long price,
            int quantity,
            CartItemStatus status,
            @Nullable UUID thumbnailFileId,
            Instant addedAt) {}
}
