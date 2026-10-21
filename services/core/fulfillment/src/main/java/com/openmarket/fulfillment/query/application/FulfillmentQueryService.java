package com.openmarket.fulfillment.query.application;

import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.fulfillment.query.dao.FulfillmentQueryDao;
import com.openmarket.fulfillment.query.dto.SellerFulfillmentView;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송 조회(docs/architecture/fulfillment.md "API"). 다른 판매자의 하위 주문이나 배송이 아직 없는 하위 주문은 404 {@code
 * FULFILLMENT_NOT_FOUND}다.
 */
@Service
public class FulfillmentQueryService {

    private final FulfillmentQueryDao dao;

    public FulfillmentQueryService(FulfillmentQueryDao dao) {
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    public SellerFulfillmentView sellerFulfillment(UUID sellerId, UUID sellerOrderId) {
        return dao.findForSeller(sellerId, sellerOrderId)
                .orElseThrow(
                        () -> new NotFoundException("FULFILLMENT_NOT_FOUND", "배송 정보를 찾을 수 없습니다."));
    }
}
