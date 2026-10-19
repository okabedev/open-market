package com.openmarket.catalog.command.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 상품 저장소. 구현은 {@code infra}에 있다. */
public interface ProductRepository {

    Product save(Product product);

    /** 잠그지 않고 읽는다. 상품 행을 바꾸지 않는 작업(재고 조정)이 소유자와 상태만 본다. */
    Optional<Product> findById(UUID id);

    /** 상품 행을 잠그고 읽는다. 동시 수정이 겹쳐도 revision과 이벤트 순서가 어긋나지 않는다. */
    Optional<Product> findForUpdateById(UUID id);

    /** 판매자의 상품을 모두 잠그고 읽는다(판매자 정지·해제). */
    List<Product> findForUpdateBySellerId(UUID sellerId);
}
