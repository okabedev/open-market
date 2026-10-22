package com.openmarket.fulfillment.query.application;

import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.fulfillment.query.dao.FulfillmentQueryDao;
import com.openmarket.fulfillment.query.dto.BuyerFulfillmentView;
import com.openmarket.fulfillment.query.dto.MediationClaimView;
import com.openmarket.fulfillment.query.dto.PageView;
import com.openmarket.fulfillment.query.dto.SellerFulfillmentView;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송 조회(docs/architecture/fulfillment.md "API"). 남의 하위 주문이나 배송이 아직 없는 하위 주문은 404 {@code
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
                .orElseThrow(FulfillmentQueryService::notFound);
    }

    @Transactional(readOnly = true)
    public BuyerFulfillmentView buyerFulfillment(UUID buyerId, UUID sellerOrderId) {
        return dao.findForBuyer(buyerId, sellerOrderId)
                .orElseThrow(FulfillmentQueryService::notFound);
    }

    /** 관리자 중재 목록(docs/architecture/fulfillment.md "중재"). */
    @Transactional(readOnly = true)
    public PageView<MediationClaimView> mediatingClaims(int page, int size) {
        return dao.findMediatingClaims(page, size);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("FULFILLMENT_NOT_FOUND", "배송 정보를 찾을 수 없습니다.");
    }
}
