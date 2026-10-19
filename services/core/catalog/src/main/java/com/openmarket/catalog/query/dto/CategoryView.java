package com.openmarket.catalog.query.dto;

import java.util.List;
import java.util.UUID;

/**
 * 카테고리 트리의 한 노드. 표시 순서대로다.
 *
 * @param children 소분류. 소분류에는 비어 있다. 상품은 소분류에만 붙는다
 */
public record CategoryView(UUID categoryId, String name, List<CategoryView> children) {}
