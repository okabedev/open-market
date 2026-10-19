package com.openmarket.catalog.ui.web;

import com.openmarket.catalog.query.application.CategoryQueryService;
import com.openmarket.catalog.query.application.ProductQueryService;
import com.openmarket.catalog.query.dto.CategoryView;
import com.openmarket.catalog.query.dto.ProductDetailView;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 구매자·공개 조회. 토큰 없이 부른다(config-repo catalog.yml의 {@code public-paths}). 상품 목록·검색은 search가 맡는다. */
@RestController
public class CatalogQueryController {

    private final CategoryQueryService categories;
    private final ProductQueryService products;

    public CatalogQueryController(CategoryQueryService categories, ProductQueryService products) {
        this.categories = categories;
        this.products = products;
    }

    /** 카테고리 트리. 대분류와 소분류, 표시 순서대로. */
    @GetMapping("/v1/categories")
    public List<CategoryView> categories() {
        return categories.tree();
    }

    /** 상품 상세. 판매 중이거나 판매 중지된 상품만 보이고, 나머지는 404 {@code CATALOG_PRODUCT_NOT_FOUND}. */
    @GetMapping("/v1/products/{productId}")
    public ProductDetailView product(@PathVariable UUID productId) {
        return products.publicDetail(productId);
    }
}
