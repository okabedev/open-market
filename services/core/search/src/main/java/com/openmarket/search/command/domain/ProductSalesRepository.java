package com.openmarket.search.command.domain;

import java.util.UUID;

/**
 * 판매량 저장소(Postgres {@code product_sales}, docs/architecture/search.md "판매량"). 더하기는 다시 처리하면 두 번
 * 더해지므로 이벤트 처리와 같은 DB 트랜잭션에서 한다. 구현은 {@code infra}에 있다.
 */
public interface ProductSalesRepository {

    /** 하위 주문을 센 것으로 기록한다. 이미 셌으면 false다. */
    boolean markCounted(UUID sellerOrderId);

    /** 상품 판매량에 수량을 더하고(행 잠금) 더한 뒤의 판매량과 리비전을 돌려준다. */
    SearchProduct.Sales add(UUID productId, long quantity);
}
