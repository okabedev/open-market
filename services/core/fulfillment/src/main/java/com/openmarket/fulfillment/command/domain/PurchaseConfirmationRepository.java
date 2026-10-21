package com.openmarket.fulfillment.command.domain;

import java.util.UUID;

/** 구매확정 저장소. 구현은 {@code infra}에 있다. */
public interface PurchaseConfirmationRepository {

    PurchaseConfirmation save(PurchaseConfirmation confirmation);

    boolean existsBySellerOrderId(UUID sellerOrderId);
}
