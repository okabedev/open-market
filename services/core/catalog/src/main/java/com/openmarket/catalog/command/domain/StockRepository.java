package com.openmarket.catalog.command.domain;

import java.util.List;
import java.util.UUID;

/** 재고 저장소. 구현은 {@code infra}에 있다. */
public interface StockRepository {

    Stock save(Stock stock);

    List<Stock> findByProductId(UUID productId);
}
