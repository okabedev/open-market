package com.openmarket.fulfillment.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 배송 항목. 주문 당시 스냅샷({@code OrderPaid})이고 바뀌지 않는다. 옵션은 두 개까지다(catalog 상품 옵션 규칙). */
@Embeddable
public class ShipmentLine {

    @Column(name = "order_line_id", nullable = false)
    private UUID orderLineId;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "option_value_1")
    private @Nullable String optionValue1;

    @Column(name = "option_value_2")
    private @Nullable String optionValue2;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long amount;

    protected ShipmentLine() {}

    public ShipmentLine(
            UUID orderLineId,
            UUID skuId,
            UUID productId,
            String productName,
            List<String> optionValues,
            UUID categoryId,
            long unitPrice,
            int quantity,
            long amount) {
        if (optionValues.size() > 2) {
            throw new IllegalArgumentException("옵션은 두 개까지다: " + optionValues.size());
        }
        this.orderLineId = orderLineId;
        this.skuId = skuId;
        this.productId = productId;
        this.productName = productName;
        this.optionValue1 = optionValues.isEmpty() ? null : optionValues.get(0);
        this.optionValue2 = optionValues.size() < 2 ? null : optionValues.get(1);
        this.categoryId = categoryId;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.amount = amount;
    }

    public UUID orderLineId() {
        return orderLineId;
    }

    public UUID skuId() {
        return skuId;
    }

    public UUID productId() {
        return productId;
    }

    public String productName() {
        return productName;
    }

    public List<String> optionValues() {
        List<String> values = new ArrayList<>(2);
        if (optionValue1 != null) {
            values.add(optionValue1);
        }
        if (optionValue2 != null) {
            values.add(optionValue2);
        }
        return values;
    }

    public UUID categoryId() {
        return categoryId;
    }

    public long unitPrice() {
        return unitPrice;
    }

    public int quantity() {
        return quantity;
    }

    public long amount() {
        return amount;
    }
}
