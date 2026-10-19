package com.openmarket.catalog.query.application;

import com.openmarket.catalog.query.dao.StockAdjustmentQueryDao;
import com.openmarket.catalog.query.dto.PageView;
import com.openmarket.catalog.query.dto.StockAdjustmentView;
import com.openmarket.common.core.error.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 조정 이력 조회. 다른 판매자의 상품은 404 {@code CATALOG_PRODUCT_NOT_FOUND}, 상품에 없는 SKU는 404 {@code
 * CATALOG_SKU_NOT_FOUND}.
 */
@Service
public class StockAdjustmentQueryService {

    private final StockAdjustmentQueryDao dao;

    public StockAdjustmentQueryService(StockAdjustmentQueryDao dao) {
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    public PageView<StockAdjustmentView> history(
            UUID sellerId, UUID productId, UUID skuId, int page, int size) {
        if (!dao.isOwnedBy(sellerId, productId)) {
            throw new NotFoundException("CATALOG_PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다.");
        }
        if (!dao.skuBelongsTo(productId, skuId)) {
            throw new NotFoundException("CATALOG_SKU_NOT_FOUND", "옵션 조합을 찾을 수 없습니다.");
        }
        return dao.findBySku(skuId, page, size);
    }
}
