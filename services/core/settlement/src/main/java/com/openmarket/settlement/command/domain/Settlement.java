package com.openmarket.settlement.command.domain;

import com.openmarket.common.core.id.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 정산(docs/glossary.md "정산", STL-2, STL-3). 판매자·정산 기간마다 하나이고(DB 유니크) 금액은 그 기간 정산 대상의 합이다. 확정한 정산은 다시
 * 계산하지 않는다. 규칙은 docs/architecture/settlement.md "정산 확정", "지급 처리".
 */
@Entity
@Table(name = "settlement")
public class Settlement {

    @Id private UUID id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "sales_amount", nullable = false)
    private long salesAmount;

    @Column(name = "commission_amount", nullable = false)
    private long commissionAmount;

    @Column(name = "seller_coupon_discount", nullable = false)
    private long sellerCouponDiscount;

    // 정산 금액. 0 이하일 수 있고, 그때 지급할 금액은 0원이다
    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @Column(name = "paid_out_at")
    private @Nullable Instant paidOutAt;

    // 지급 처리한 관리자
    @Column(name = "paid_out_by")
    private @Nullable UUID paidOutBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Settlement() {}

    /**
     * 판매자·기간의 확정되지 않은 정산 대상을 모아 정산을 확정한다. 대상에 정산 ID를 남긴다. 대상이 없거나, 다른 판매자·기간의 대상이 섞이면 {@link
     * IllegalArgumentException}, 이미 정산된 대상이면 {@link IllegalStateException}이다.
     */
    public static Settlement confirm(
            UUID sellerId, SettlementPeriod period, List<SettlementItem> items, Instant now) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("정산할 대상이 없다");
        }
        for (SettlementItem item : items) {
            if (!item.sellerId().equals(sellerId)
                    || !item.periodStart().equals(period.start())
                    || !item.periodEnd().equals(period.end())) {
                throw new IllegalArgumentException("다른 판매자·기간의 대상이다: " + item.id());
            }
        }
        Settlement settlement = new Settlement();
        settlement.id = Ids.newId();
        settlement.sellerId = sellerId;
        settlement.periodStart = period.start();
        settlement.periodEnd = period.end();
        settlement.itemCount = items.size();
        settlement.salesAmount = items.stream().mapToLong(SettlementItem::salesAmount).sum();
        settlement.commissionAmount =
                items.stream().mapToLong(SettlementItem::commissionAmount).sum();
        settlement.sellerCouponDiscount =
                items.stream().mapToLong(SettlementItem::sellerCouponDiscount).sum();
        settlement.amount = items.stream().mapToLong(SettlementItem::settlementAmount).sum();
        settlement.status = SettlementStatus.CONFIRMED;
        settlement.confirmedAt = now;
        settlement.updatedAt = now;
        items.forEach(item -> item.settle(settlement.id, now));
        return settlement;
    }

    /** 지급 완료로 기록한다. 이미 지급했으면 false이고 아무것도 바꾸지 않는다. */
    public boolean payOut(UUID adminId, Instant now) {
        if (status == SettlementStatus.PAID_OUT) {
            return false;
        }
        status = SettlementStatus.PAID_OUT;
        paidOutAt = now;
        paidOutBy = adminId;
        updatedAt = now;
        return true;
    }

    public UUID id() {
        return id;
    }

    public UUID sellerId() {
        return sellerId;
    }

    public Instant periodStart() {
        return periodStart;
    }

    public Instant periodEnd() {
        return periodEnd;
    }

    public int itemCount() {
        return itemCount;
    }

    public long salesAmount() {
        return salesAmount;
    }

    public long commissionAmount() {
        return commissionAmount;
    }

    public long sellerCouponDiscount() {
        return sellerCouponDiscount;
    }

    public long amount() {
        return amount;
    }

    public SettlementStatus status() {
        return status;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }

    public @Nullable Instant paidOutAt() {
        return paidOutAt;
    }

    public @Nullable UUID paidOutBy() {
        return paidOutBy;
    }
}
