package com.openmarket.catalog.query.dto;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * SKU 하나의 주문용 요약. order가 장바구니를 보여 주고 주문할 때 가격과 판매 상태를 확인하는 데 쓴다(내부 API).
 *
 * @param productStatus 상품 상태. 주문할 수 있는 것은 {@code ON_SALE}뿐이다
 * @param optionNames 상품의 옵션 이름. {@code optionValues}가 이 순서다
 * @param availableQuantity 판매 가능 수량(실재고 - 예약)
 * @param thumbnailFileId 상품 대표 이미지. 이미지가 없으면 null
 */
public record SkuSummaryView(
        UUID skuId,
        UUID productId,
        String productName,
        ProductStatus productStatus,
        UUID sellerId,
        String storeName,
        UUID categoryId,
        List<String> optionNames,
        List<String> optionValues,
        long price,
        int availableQuantity,
        @Nullable UUID thumbnailFileId) {}
