package com.openmarket.catalog.command.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 재고 저장소. 구현은 {@code infra}에 있다. */
public interface StockRepository {

    Stock save(Stock stock);

    /** 재고 행을 잠그고 읽는다(재고 조정). */
    Optional<Stock> findForUpdateBySkuId(UUID skuId);

    List<Stock> findByProductId(UUID productId);
}
