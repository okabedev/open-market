package com.openmarket.catalog.command.application;

import java.util.List;
import java.util.UUID;

/**
 * 상품을 등록한다(CAT-1).
 *
 * @param imageFileIds file에 올린 이미지 fileId. 순서대로
 * @param optionNames 옵션 이름 0~2개. 등록 뒤에는 바꾸지 않는다
 */
public record RegisterProductCommand(
        UUID sellerId,
        UUID categoryId,
        String name,
        String description,
        List<UUID> imageFileIds,
        List<String> optionNames,
        List<Sku> skus) {

    /**
     * 옵션 조합 한 줄.
     *
     * @param optionValues 옵션 이름 순서대로 값 하나씩
     * @param price 원
     * @param initialStock 초기 재고
     */
    public record Sku(List<String> optionValues, long price, int initialStock) {}
}
