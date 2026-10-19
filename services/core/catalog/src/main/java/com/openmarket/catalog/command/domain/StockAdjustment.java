package com.openmarket.catalog.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 재고 조정 이력(docs/glossary.md "재고 조정"). 만든 뒤 바꾸지 않는다. */
@Entity
@Table(name = "stock_adjustment")
public class StockAdjustment {

    static final int REASON_MAX = 100;

    @Id private UUID id;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int delta;

    @Column(name = "on_hand_after", nullable = false)
    private int onHandAfter;

    private @Nullable String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockAdjustment() {}

    private StockAdjustment(UUID id, Stock stock, int delta, @Nullable String reason, Instant now) {
        this.id = id;
        this.skuId = stock.skuId();
        this.productId = stock.productId();
        this.delta = delta;
        this.onHandAfter = stock.onHand();
        this.reason = reason;
        this.createdAt = now;
    }

    /**
     * 조정한 재고의 이력을 남긴다. 조정 뒤에 부른다.
     *
     * @param reason 선택. 앞뒤 공백을 지우고 비면 없음으로 둔다
     */
    public static StockAdjustment record(
            UUID id, Stock adjusted, int delta, @Nullable String reason, Instant now) {
        return new StockAdjustment(id, adjusted, delta, normalizeReason(reason), now);
    }

    /** 사유를 검사한다. 조정 전에 불러 잘못된 입력이면 재고를 바꾸지 않는다. */
    public static @Nullable String normalizeReason(@Nullable String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String text = reason.strip();
        if (text.length() > REASON_MAX) {
            throw new InvalidProductException("조정 사유는 100자까지입니다.");
        }
        return text;
    }

    public UUID id() {
        return id;
    }

    public UUID skuId() {
        return skuId;
    }

    public int delta() {
        return delta;
    }

    public int onHandAfter() {
        return onHandAfter;
    }

    public @Nullable String reason() {
        return reason;
    }
}
