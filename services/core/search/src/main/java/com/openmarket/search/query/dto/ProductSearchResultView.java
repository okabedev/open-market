package com.openmarket.search.query.dto;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 검색 결과 한 쪽(커서 페이징).
 *
 * @param totalCount 조건에 맞는 상품 수. 1만 건이 넘으면 10000이다
 * @param nextCursor 다음 쪽 커서. 마지막 쪽이면 null
 */
public record ProductSearchResultView(
        List<ProductSearchItemView> items, long totalCount, @Nullable String nextCursor) {}
