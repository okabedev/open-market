package com.openmarket.catalog.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** 재고(docs/glossary.md "재고"). SKU마다 하나다. 판매 가능 수량은 {@code onHand - reserved}다. 예약은 M3에서 더한다. */
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

    /**
     * 판매자가 실재고를 늘리거나 줄인다(CAT-3). 이력은 {@link StockAdjustment}로 따로 남긴다.
     *
     * @param delta 0이 아닌 -999,999~999,999. 어기면 400
     * @throws StockOutOfRangeException 조정 뒤 실재고가 예약 수량보다 작거나 999,999보다 크다
     */
    public void adjust(int delta, Instant now) {
        if (delta == 0 || Math.abs(delta) > MAX_ON_HAND) {
            throw new InvalidProductException("증감 수량은 0이 아닌 -999,999~999,999입니다.");
        }
        long after = (long) onHand + delta;
        if (after < reserved || after > MAX_ON_HAND) {
            throw new StockOutOfRangeException();
        }
        this.onHand = (int) after;
        this.updatedAt = now;
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
