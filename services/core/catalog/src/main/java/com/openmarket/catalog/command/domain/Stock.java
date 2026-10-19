package com.openmarket.catalog.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * 재고(docs/glossary.md "재고"). SKU마다 하나다. 판매 가능 수량은 {@code onHand - reserved}다. 조정과 이력은 재고 조정 기능에서,
 * 예약은 M3에서 더한다.
 */
@Entity
@Table(name = "stock")
public class Stock {

    /** SKU 하나의 최대 실재고. */
    public static final int MAX_ON_HAND = 999_999;

    @Id
    @Column(name = "sku_id")
    private UUID skuId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Stock() {}

    private Stock(UUID skuId, UUID productId, int onHand, Instant now) {
        this.skuId = skuId;
        this.productId = productId;
        this.onHand = onHand;
        this.reserved = 0;
        this.updatedAt = now;
    }

    /** 새 SKU의 재고를 초기 수량으로 만든다. */
    public static Stock create(UUID skuId, UUID productId, int onHand, Instant now) {
        if (onHand < 0 || onHand > MAX_ON_HAND) {
            throw new InvalidProductException("재고는 0부터 999,999까지입니다.");
        }
        return new Stock(skuId, productId, onHand, now);
    }

    public int available() {
        return onHand - reserved;
    }

    public boolean inStock() {
        return available() > 0;
    }

    public UUID skuId() {
        return skuId;
    }

    public UUID productId() {
        return productId;
    }

    public int onHand() {
        return onHand;
    }

    public int reserved() {
        return reserved;
    }
}
