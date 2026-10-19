package com.openmarket.catalog.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 판매 가능 여부(docs/glossary.md "판매 가능 여부"). 상품마다 하나다. 값이 바뀔 때마다 리비전을 올린다. 상품 이벤트와 토픽이 다른 {@code
 * StockAvailabilityChanged}의 순서를 소비자가 이 리비전으로 맞춘다(docs/architecture/context-map.md
 * "catalog.stock").
 */
@Entity
@Table(name = "product_availability")
public class ProductAvailability {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "in_stock", nullable = false)
    private boolean inStock;

    @Column(nullable = false)
    private long revision;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductAvailability() {}

    private ProductAvailability(UUID productId, boolean inStock, Instant now) {
        this.productId = productId;
        this.inStock = inStock;
        this.revision = 1;
        this.updatedAt = now;
    }

    /** 등록한 상품의 판매 가능 여부를 리비전 1로 만든다. 이 값은 등록 스냅샷에 들어간다. */
    public static ProductAvailability create(UUID productId, boolean inStock, Instant now) {
        return new ProductAvailability(productId, inStock, now);
    }

    /**
     * 다시 계산한 값을 반영한다.
     *
     * @return 값이 바뀌어 리비전을 올렸으면 true
     */
    public boolean change(boolean inStock, Instant now) {
        if (this.inStock == inStock) {
            return false;
        }
        this.inStock = inStock;
        this.revision += 1;
        this.updatedAt = now;
        return true;
    }

    public UUID productId() {
        return productId;
    }

    public boolean inStock() {
        return inStock;
    }

    public long revision() {
        return revision;
    }
}
