package com.openmarket.catalog.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 상품 저장소. 구현은 {@code infra}에 있다. */
public interface ProductRepository {

    Product save(Product product);

    /** 상품 행을 잠그고 읽는다. 동시 수정이 겹쳐도 revision과 이벤트 순서가 어긋나지 않는다. */
    Optional<Product> findForUpdateById(UUID id);
}
