package com.openmarket.catalog.query.application;

import com.openmarket.catalog.query.dao.ProductQueryDao;
import com.openmarket.catalog.query.dto.SkuSummaryView;
import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SKU 일괄 조회(order용 내부 API). 상품 상태와 상관없이 있는 SKU는 모두 준다. 주문할 수 있는지는 order가 판단한다. */
@Service
public class SkuQueryService {

    /** 한 번에 조회하는 SKU 수 상한. 장바구니 상한(order)보다 크다. */
    static final int MAX_IDS = 100;

    private final ProductQueryDao dao;

    public SkuQueryService(ProductQueryDao dao) {
        this.dao = dao;
    }

    /** 없는 SKU는 결과에서 빠진다. 순서는 정하지 않는다. */
    @Transactional(readOnly = true)
    public List<SkuSummaryView> summaries(List<UUID> skuIds) {
        List<UUID> ids = skuIds.stream().distinct().toList();
        if (ids.isEmpty() || ids.size() > MAX_IDS) {
            throw new BusinessException(
                    ErrorType.INVALID, "CATALOG_INVALID_INPUT", "SKU ID는 1~100개입니다.");
        }
        return dao.findSkuSummaries(ids);
    }
}
