package com.openmarket.catalog.ui.web;

import com.openmarket.catalog.query.application.ProductQueryService;
import com.openmarket.catalog.query.dto.AdminProductSummaryView;
import com.openmarket.catalog.query.dto.PageView;
import com.openmarket.catalog.query.dto.ProductStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 상품 목록(ADM-2). 쪽 번호 목록이다. */
@RestController
@RequestMapping("/v1/admin/products")
public class AdminProductQueryController {

    private final ProductQueryService products;

    public AdminProductQueryController(ProductQueryService products) {
        this.products = products;
    }

    /** 상태·판매자로 거른다. 주지 않으면 전체. 최근 수정 순. */
    @GetMapping
    public PageView<AdminProductSummaryView> list(
            @RequestParam(required = false) @Nullable ProductStatus status,
            @RequestParam(required = false) @Nullable UUID sellerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return products.adminProducts(status, sellerId, page, size);
    }
}
