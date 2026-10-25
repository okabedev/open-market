package com.openmarket.settlement.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** 정산 대상의 주문 항목 하나. 받은 때의 요율과 수수료를 고정한다. */
@Embeddable
public class SettlementItemLine {

    @Column(nullable = false)
    private int position;

    @Column(name = "order_line_id", nullable = false)
    private UUID orderLineId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long amount;

    @Column(name = "commission_rate_bp", nullable = false)
    private int commissionRateBp;

    @Column(name = "commission_amount", nullable = false)
    private long commissionAmount;

    protected SettlementItemLine() {}

    SettlementItemLine(int position, SettlementItem.Line line, int commissionRateBp) {
        this.position = position;
        this.orderLineId = line.orderLineId();
        this.productId = line.productId();
        this.productName = line.productName();
        this.categoryId = line.categoryId();
        this.quantity = line.quantity();
        this.amount = line.amount();
        this.commissionRateBp = commissionRateBp;
        this.commissionAmount = CommissionRate.commission(line.amount(), commissionRateBp);
    }

    public UUID orderLineId() {
        return orderLineId;
    }

    public UUID productId() {
        return productId;
    }

    public String productName() {
        return productName;
    }

    public UUID categoryId() {
        return categoryId;
    }

    public int quantity() {
        return quantity;
    }

    public long amount() {
        return amount;
    }

    public int commissionRateBp() {
        return commissionRateBp;
    }

    public long commissionAmount() {
        return commissionAmount;
    }
}
