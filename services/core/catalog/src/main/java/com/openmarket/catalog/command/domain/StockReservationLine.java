package com.openmarket.catalog.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** 예약한 SKU 하나와 수량. 상품 ID는 판매 가능 여부를 다시 계산할 때 쓴다. */
@Embeddable
public class StockReservationLine {

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    protected StockReservationLine() {}

    StockReservationLine(UUID skuId, UUID productId, int quantity) {
        this.skuId = skuId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public UUID skuId() {
        return skuId;
    }

    public UUID productId() {
        return productId;
    }

    public int quantity() {
        return quantity;
    }
}
