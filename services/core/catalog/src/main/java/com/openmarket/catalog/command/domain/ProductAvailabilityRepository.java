package com.openmarket.catalog.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 판매 가능 여부 저장소. 구현은 {@code infra}에 있다. */
public interface ProductAvailabilityRepository {

    ProductAvailability save(ProductAvailability availability);

    Optional<ProductAvailability> findByProductId(UUID productId);

    /** 행을 잠그고 읽는다. 같은 상품의 SKU를 동시에 조정해도 마지막 트랜잭션이 모든 재고 변경을 보고 계산한다. */
    Optional<ProductAvailability> findForUpdateByProductId(UUID productId);
}
