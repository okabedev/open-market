package com.openmarket.catalog.command.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 상품 단위 판매 가능 여부를 재고에서 다시 계산한다(docs/architecture/catalog.md "판매 가능 여부와 품절 전환 이벤트"). 재고를 바꾼 유스케이스가
 * 같은 트랜잭션에서 재고를 바꾼 뒤에 부르고, 돌려받은 이벤트를 outbox에 넣는다.
 */
public class ProductAvailabilityCalculator {

    private final StockRepository stocks;
    private final ProductAvailabilityRepository availabilities;

    public ProductAvailabilityCalculator(
            StockRepository stocks, ProductAvailabilityRepository availabilities) {
        this.stocks = stocks;
        this.availabilities = availabilities;
    }

    /** 등록한 상품의 판매 가능 여부를 리비전 1로 만든다. 이벤트는 없다(등록 스냅샷에 들어간다). */
    public void create(UUID productId, Instant now) {
        availabilities.save(ProductAvailability.create(productId, computeInStock(productId), now));
    }

    /**
     * 행을 잠근 뒤 재고를 읽어 계산한다. 같은 상품의 다른 SKU를 동시에 바꿔도 마지막 트랜잭션이 모든 변경을 본다.
     *
     * @return 값이 바뀌었으면 낼 이벤트
     */
    public Optional<StockAvailabilityChanged> refresh(UUID productId, Instant now) {
        ProductAvailability availability =
                availabilities
                        .findForUpdateByProductId(productId)
                        .orElseThrow(
                                () -> new IllegalStateException("판매 가능 여부가 없는 상품: " + productId));
        if (!availability.change(computeInStock(productId), now)) {
            return Optional.empty();
        }
        availabilities.save(availability);
        return Optional.of(
                new StockAvailabilityChanged(
                        productId, availability.inStock(), availability.revision()));
    }

    private boolean computeInStock(UUID productId) {
        return stocks.findByProductId(productId).stream().anyMatch(Stock::inStock);
    }
}
