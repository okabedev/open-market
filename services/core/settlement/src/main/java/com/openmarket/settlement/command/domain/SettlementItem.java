package com.openmarket.settlement.command.domain;

import com.openmarket.common.core.id.Ids;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * 정산 대상(docs/glossary.md "정산 대상", STL-1). 구매확정된 하위 주문 하나이고 하위 주문 ID로 한 번만 생긴다(DB 유니크). 금액 계산은
 * docs/architecture/settlement.md "정산 대상". 구매확정 뒤에는 바뀌지 않으므로 금액을 만들 때 고정한다.
 */
@Entity
@Table(name = "settlement_item")
public class SettlementItem {

    @Id private UUID id;

    @Column(name = "seller_order_id", nullable = false)
    private UUID sellerOrderId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "sales_amount", nullable = false)
    private long salesAmount;

    @Column(name = "commission_amount", nullable = false)
    private long commissionAmount;

    @Column(name = "seller_coupon_discount", nullable = false)
    private long sellerCouponDiscount;

    // 판매 금액 − 수수료 − 판매자 쿠폰 부담분. 0 이하일 수 있다
    @Column(name = "settlement_amount", nullable = false)
    private long settlementAmount;

    // 확정한 정산. 확정되지 않았으면 null
    @Column(name = "settlement_id")
    private @Nullable UUID settlementId;

    @ElementCollection
    @CollectionTable(
            name = "settlement_item_line",
            joinColumns = @JoinColumn(name = "settlement_item_id"))
    @OrderBy("position")
    private List<SettlementItemLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected SettlementItem() {}

    /**
     * 구매확정한 하위 주문으로 정산 대상을 만든다. 항목마다 {@code rateOf}(카테고리 → 요율 bp)로 받은 때의 요율을 고정하고 수수료를 {@code
     * floor(항목 금액 × 요율 / 10,000)}로 계산한다.
     */
    public static SettlementItem create(
            Purchase purchase, ToIntFunction<UUID> rateOf, SettlementPeriod period, Instant now) {
        SettlementItem item = new SettlementItem();
        item.id = Ids.newId();
        item.sellerOrderId = purchase.sellerOrderId();
        item.orderId = purchase.orderId();
        item.sellerId = purchase.sellerId();
        item.confirmedAt = purchase.confirmedAt();
        item.periodStart = period.start();
        item.periodEnd = period.end();
        List<Line> lines = purchase.lines();
        item.lines =
                new ArrayList<>(
                        IntStream.range(0, lines.size())
                                .mapToObj(
                                        i ->
                                                new SettlementItemLine(
                                                        i,
                                                        lines.get(i),
                                                        CommissionRate.checked(
                                                                rateOf.applyAsInt(
                                                                        lines.get(i)
                                                                                .categoryId()))))
                                .toList());
        item.salesAmount = item.lines.stream().mapToLong(SettlementItemLine::amount).sum();
        item.commissionAmount =
                item.lines.stream().mapToLong(SettlementItemLine::commissionAmount).sum();
        item.sellerCouponDiscount = purchase.sellerCouponDiscount();
        item.settlementAmount =
                item.salesAmount - item.commissionAmount - item.sellerCouponDiscount;
        item.createdAt = now;
        item.updatedAt = now;
        return item;
    }

    public boolean isSettled() {
        return settlementId != null;
    }

    /** 정산에 넣는다. {@link Settlement#confirm}만 부른다. */
    void settle(UUID settlementId, Instant now) {
        if (this.settlementId != null) {
            throw new IllegalStateException("이미 정산된 대상이다: " + id);
        }
        this.settlementId = settlementId;
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID sellerOrderId() {
        return sellerOrderId;
    }

    public UUID orderId() {
        return orderId;
    }

    public UUID sellerId() {
        return sellerId;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }

    public Instant periodStart() {
        return periodStart;
    }

    public Instant periodEnd() {
        return periodEnd;
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

    public long settlementAmount() {
        return settlementAmount;
    }

    public @Nullable UUID settlementId() {
        return settlementId;
    }

    public List<SettlementItemLine> lines() {
        return List.copyOf(lines);
    }

    /**
     * 구매확정한 하위 주문({@code PurchaseConfirmed}).
     *
     * @param sellerCouponDiscount 판매자 쿠폰 할인. 플랫폼 쿠폰 할인은 플랫폼이 부담해 싣지 않는다
     */
    public record Purchase(
            UUID orderId,
            UUID sellerOrderId,
            UUID sellerId,
            Instant confirmedAt,
            long sellerCouponDiscount,
            List<Line> lines) {

        public Purchase {
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("주문 항목이 없다: " + sellerOrderId);
            }
            if (sellerCouponDiscount < 0) {
                throw new IllegalArgumentException("판매자 쿠폰 할인은 0 이상이다");
            }
            lines = List.copyOf(lines);
        }
    }

    /**
     * 주문 항목 하나.
     *
     * @param amount 개당 가격 × 수량. 쿠폰을 빼기 전이다
     */
    public record Line(
            UUID orderLineId,
            UUID productId,
            String productName,
            UUID categoryId,
            int quantity,
            long amount) {

        public Line {
            if (quantity < 1 || amount < 0) {
                throw new IllegalArgumentException("수량은 1 이상, 금액은 0 이상이다");
            }
        }
    }
}
