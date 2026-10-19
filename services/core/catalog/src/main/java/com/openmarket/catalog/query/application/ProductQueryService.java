package com.openmarket.catalog.query.application;

import com.openmarket.catalog.query.dao.ProductQueryDao;
import com.openmarket.catalog.query.dto.AdminProductSummaryView;
import com.openmarket.catalog.query.dto.PageView;
import com.openmarket.catalog.query.dto.ProductDetailView;
import com.openmarket.catalog.query.dto.ProductStatus;
import com.openmarket.catalog.query.dto.SellerProductSummaryView;
import com.openmarket.catalog.query.dto.SellerProductView;
import com.openmarket.common.core.error.NotFoundException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 조회. 보이지 않는 상품(등록 중, 제재, 다른 판매자의 상품)은 404 {@code CATALOG_PRODUCT_NOT_FOUND}다. */
@Service
public class ProductQueryService {

    private final ProductQueryDao dao;

    public ProductQueryService(ProductQueryDao dao) {
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    public ProductDetailView publicDetail(UUID productId) {
        return dao.findPublic(productId).orElseThrow(ProductQueryService::notFound);
    }

    @Transactional(readOnly = true)
    public SellerProductView sellerDetail(UUID sellerId, UUID productId) {
        return dao.findForSeller(sellerId, productId).orElseThrow(ProductQueryService::notFound);
    }

    @Transactional(readOnly = true)
    public PageView<SellerProductSummaryView> sellerProducts(
            UUID sellerId, @Nullable ProductStatus status, int page, int size) {
        return dao.findSellerProducts(sellerId, status, page, size);
    }

    @Transactional(readOnly = true)
    public PageView<AdminProductSummaryView> adminProducts(
            @Nullable ProductStatus status, @Nullable UUID sellerId, int page, int size) {
        return dao.findAdminProducts(status, sellerId, page, size);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("CATALOG_PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다.");
    }
}
