package com.openmarket.search.query.application;

import com.openmarket.search.query.dao.ProductSearchQueryDao;
import com.openmarket.search.query.dto.ProductSearchResultView;
import com.openmarket.search.query.dto.ProductSearchSort;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** 구매자 상품 검색(SRC-1, SRC-3). 공백뿐인 검색어는 없는 것으로 본다. */
@Service
public class ProductSearchQueryService {

    private final ProductSearchQueryDao dao;

    public ProductSearchQueryService(ProductSearchQueryDao dao) {
        this.dao = dao;
    }

    public ProductSearchResultView search(
            @Nullable String q,
            @Nullable UUID categoryId,
            @Nullable Long minPrice,
            @Nullable Long maxPrice,
            @Nullable Integer minRating,
            ProductSearchSort sort,
            @Nullable String cursor,
            int size) {
        String keyword = q == null || q.isBlank() ? null : q.strip();
        return dao.search(
                new ProductSearchQueryDao.Condition(
                        keyword, categoryId, minPrice, maxPrice, minRating, sort, cursor, size));
    }
}
