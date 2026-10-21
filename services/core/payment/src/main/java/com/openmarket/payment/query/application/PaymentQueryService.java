package com.openmarket.payment.query.application;

import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.payment.query.dao.PaymentQueryDao;
import com.openmarket.payment.query.dto.PaymentView;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 결제 조회(docs/architecture/payment.md "조회"). 다른 회원의 결제는 없는 결제처럼 404 {@code PAYMENT_NOT_FOUND}다. */
@Service
public class PaymentQueryService {

    private final PaymentQueryDao dao;

    public PaymentQueryService(PaymentQueryDao dao) {
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    public PaymentView buyerPayment(UUID buyerId, UUID orderId) {
        return dao.findForBuyer(buyerId, orderId)
                .orElseThrow(() -> new NotFoundException("PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다."));
    }
}
