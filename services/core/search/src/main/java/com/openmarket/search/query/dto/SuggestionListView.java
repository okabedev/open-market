package com.openmarket.search.query.dto;

import java.util.List;

/**
 * 자동완성 목록. 판매량 많은 순, 최대 10개다. 페이징하지 않는다.
 *
 * @param items 같은 상품명은 하나만 있다
 */
public record SuggestionListView(List<SuggestionView> items) {

    public SuggestionListView {
        items = List.copyOf(items);
    }
}
