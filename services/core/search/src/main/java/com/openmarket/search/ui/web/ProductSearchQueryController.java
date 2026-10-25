package com.openmarket.search.ui.web;

import com.openmarket.search.query.application.ProductSearchQueryService;
import com.openmarket.search.query.dto.ProductSearchResultView;
import com.openmarket.search.query.dto.ProductSearchSort;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 구매자 상품 검색. 토큰 없이 연다(docs/architecture/search.md "검색 API"). */
@RestController
@RequestMapping("/v1/products")
public class ProductSearchQueryController {

    private final ProductSearchQueryService search;

    public ProductSearchQueryController(ProductSearchQueryService search) {
        this.search = search;
    }

    /**
     * 판매 중인 상품을 찾는다. 품절 상품은 뒤로 간다. 다음 쪽은 응답의 {@code nextCursor}를 {@code cursor}로 넘긴다. 커서가 틀리면 400
     * {@code SEARCH_INVALID_CURSOR}.
     */
    @GetMapping
    public ProductSearchResultView search(
            @RequestParam(required = false) @Size(max = 100) @Nullable String q,
            @RequestParam(required = false) @Nullable UUID categoryId,
            @RequestParam(required = false) @Min(0) @Nullable Long minPrice,
            @RequestParam(required = false) @Min(0) @Nullable Long maxPrice,
            @RequestParam(required = false) @Min(1) @Max(5) @Nullable Integer minRating,
            @RequestParam(defaultValue = "RELEVANCE") ProductSearchSort sort,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return search.search(q, categoryId, minPrice, maxPrice, minRating, sort, cursor, size);
    }
}
