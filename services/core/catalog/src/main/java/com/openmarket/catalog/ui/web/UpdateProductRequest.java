package com.openmarket.catalog.ui.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 상품 수정. 바뀐 뒤의 전체 내용을 보낸다. 기존 옵션 조합은 모두 보내야 하고 가격만 바꿀 수 있다.
 *
 * @param imageFileIds 바뀐 뒤의 이미지 전체. 순서대로
 * @param skus 기존 조합은 {@code skuId}를 넣고, 새 조합은 {@code skuId} 없이 넣는다
 */
public record UpdateProductRequest(
        @NotNull UUID categoryId,
        @NotNull String name,
        @NotNull String description,
        @NotNull List<UUID> imageFileIds,
        @NotNull @Valid List<Sku> skus) {

    /**
     * 옵션 조합 한 줄.
     *
     * @param skuId 기존 조합의 ID. 새 조합이면 비운다
     * @param initialStock 새 조합의 초기 재고. 기존 조합이면 무시한다
     */
    @Schema(name = "UpdateProductSkuRequest")
    public record Sku(
            @Nullable UUID skuId,
            @NotNull List<String> optionValues,
            long price,
            @Nullable Integer initialStock) {}
}
