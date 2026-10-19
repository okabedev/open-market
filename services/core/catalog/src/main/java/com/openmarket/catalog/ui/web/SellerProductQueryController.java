package com.openmarket.catalog.ui.web;

import com.openmarket.catalog.query.application.ProductQueryService;
import com.openmarket.catalog.query.dto.PageView;
import com.openmarket.catalog.query.dto.ProductStatus;
import com.openmarket.catalog.query.dto.SellerProductSummaryView;
import com.openmarket.catalog.query.dto.SellerProductView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 판매자 본인 상품 조회. 주체는 토큰의 {@code sub}(= sellerId)다. */
@RestController
@RequestMapping("/v1/seller/products")
public class SellerProductQueryController {

    private final ProductQueryService products;

    public SellerProductQueryController(ProductQueryService products) {
        this.products = products;
    }

    /** 본인 상품 목록. 상태를 주지 않으면 전체. 최근 수정 순, 쪽 번호. */
    @GetMapping
    public PageView<SellerProductSummaryView> list(
            Principal principal,
            @RequestParam(required = false) @Nullable ProductStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return products.sellerProducts(sellerId(principal), status, page, size);
    }

    /** 본인 상품 상세. 재고 수량을 그대로 준다. 다른 판매자의 상품은 404 {@code CATALOG_PRODUCT_NOT_FOUND}. */
    @GetMapping("/{productId}")
    public SellerProductView detail(Principal principal, @PathVariable UUID productId) {
        return products.sellerDetail(sellerId(principal), productId);
    }

    static UUID sellerId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
