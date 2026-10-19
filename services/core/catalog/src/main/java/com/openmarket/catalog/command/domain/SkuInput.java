package com.openmarket.catalog.command.domain;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 등록·수정에서 받는 SKU 한 줄.
 *
 * @param skuId 수정에서 기존 SKU면 그 ID, 새 SKU면 null
 * @param optionValues 상품의 옵션 이름 순서대로 값 하나씩
 * @param price 원
 */
public record SkuInput(@Nullable UUID skuId, List<String> optionValues, long price) {}
