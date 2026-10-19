package com.openmarket.catalog.ui.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 상품 등록. 규칙은 docs/architecture/catalog.md "규칙".
 *
 * @param categoryId 소분류 카테고리
 * @param imageFileIds file에 올린 이미지(최대 10장). 순서대로, 첫 장이 대표 이미지
 * @param optionNames 옵션 이름 최대 2개(예: 색상, 사이즈). 없으면 옵션 없는 상품. 등록 뒤에는 바꿀 수 없다
 * @param skus 옵션 조합 1~100개
 */
public record RegisterProductRequest(
        @NotNull UUID categoryId,
        @NotNull String name,
        @NotNull String description,
        @NotNull List<UUID> imageFileIds,
        @Nullable List<String> optionNames,
        @NotNull @Valid List<Sku> skus) {

    /**
     * 옵션 조합 한 줄.
     *
     * @param optionValues 옵션 이름 순서대로 값 하나씩. 옵션이 없으면 빈 목록
     * @param price 원. 100원부터 1억 원까지 10원 단위
     * @param initialStock 초기 재고 0~999,999
     */
    public record Sku(@NotNull List<String> optionValues, long price, int initialStock) {}
}
