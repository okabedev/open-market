package com.openmarket.catalog.command.domain;

import com.openmarket.common.core.id.Ids;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 재고 예약(docs/glossary.md "재고 예약"). 주문 하나에 하나다. 재고 행은 부르는 쪽이 skuId 순서로 잠가서 넘긴다
 * (docs/architecture/catalog.md "재고 예약").
 */
@Entity
@Table(name = "stock_reservation")
public class StockReservation {

    @Id private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockReservationStatus status;

    @ElementCollection
    @CollectionTable(
            name = "stock_reservation_line",
            joinColumns = @JoinColumn(name = "reservation_id"))
    private List<StockReservationLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected StockReservation() {}

    private StockReservation(UUID orderId, StockReservationStatus status, Instant now) {
        this.id = Ids.newId();
        this.orderId = orderId;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 주문의 SKU를 모두 예약한다. 하나라도 모자라거나 재고 행이 없으면 아무것도 바꾸지 않고 {@code FAILED} 예약을 만든다.
     *
     * @param quantities SKU별 수량(1 이상)
     * @param stocks 잠근 재고 행. 없는 SKU는 빠져 있다
     */
    public static Result reserve(
            UUID orderId, Map<UUID, Integer> quantities, Map<UUID, Stock> stocks, Instant now) {
        Map<UUID, Integer> sorted = new TreeMap<>(quantities);
        List<UUID> insufficient = new ArrayList<>();
        sorted.forEach(
                (skuId, quantity) -> {
                    if (quantity < 1) {
                        throw new IllegalArgumentException("예약 수량은 1 이상이다: " + quantity);
                    }
                    @Nullable Stock stock = stocks.get(skuId);
                    if (stock == null || stock.available() < quantity) {
                        insufficient.add(skuId);
                    }
                });
        if (!insufficient.isEmpty()) {
            return new Result(
                    new StockReservation(orderId, StockReservationStatus.FAILED, now),
                    List.copyOf(insufficient));
        }
        StockReservation reservation =
                new StockReservation(orderId, StockReservationStatus.RESERVED, now);
        sorted.forEach(
                (skuId, quantity) -> {
                    Stock stock = locked(stocks, skuId);
                    stock.reserve(quantity, now);
                    reservation.lines.add(
                            new StockReservationLine(skuId, stock.productId(), quantity));
                });
        return new Result(reservation, List.of());
    }

    /** 예약 없이 취소된 주문. 뒤늦게 온 {@code OrderPlaced}가 재고를 잡지 않게 남긴다. */
    public static StockReservation releasedWithoutReservation(UUID orderId, Instant now) {
        return new StockReservation(orderId, StockReservationStatus.RELEASED, now);
    }

    /** 결제가 끝나 실재고에서 뺀다. {@code RESERVED}에서만 바뀐다. */
    public Transition commit(Map<UUID, Stock> stocks, Instant now) {
        if (status == StockReservationStatus.COMMITTED) {
            return Transition.ALREADY;
        }
        if (status != StockReservationStatus.RESERVED) {
            return Transition.INVALID;
        }
        lines.forEach(line -> locked(stocks, line.skuId()).commit(line.quantity(), now));
        status = StockReservationStatus.COMMITTED;
        updatedAt = now;
        return Transition.APPLIED;
    }

    /** 주문이 취소돼 예약을 푼다. 실패한 예약은 풀 것이 없어 {@code ALREADY}다. 확정한 예약은 풀 수 없다. */
    public Transition release(Map<UUID, Stock> stocks, Instant now) {
        if (status == StockReservationStatus.RELEASED || status == StockReservationStatus.FAILED) {
            return Transition.ALREADY;
        }
        if (status != StockReservationStatus.RESERVED) {
            return Transition.INVALID;
        }
        lines.forEach(line -> locked(stocks, line.skuId()).release(line.quantity(), now));
        status = StockReservationStatus.RELEASED;
        updatedAt = now;
        return Transition.APPLIED;
    }

    /** 예약한 SKU. 잠글 순서대로(skuId 순). */
    public SortedSet<UUID> skuIds() {
        SortedSet<UUID> ids = new TreeSet<>();
        lines.forEach(line -> ids.add(line.skuId()));
        return ids;
    }

    /** 예약한 SKU의 상품. 판매 가능 여부를 잠글 순서대로(productId 순). */
    public SortedSet<UUID> productIds() {
        SortedSet<UUID> ids = new TreeSet<>();
        lines.forEach(line -> ids.add(line.productId()));
        return ids;
    }

    public UUID orderId() {
        return orderId;
    }

    public StockReservationStatus status() {
        return status;
    }

    private static Stock locked(Map<UUID, Stock> stocks, UUID skuId) {
        @Nullable Stock stock = stocks.get(skuId);
        if (stock == null) {
            throw new IllegalStateException("잠근 재고 행이 없다: " + skuId);
        }
        return stock;
    }

    /**
     * 예약 결과.
     *
     * @param insufficientSkuIds 모자라거나 없는 SKU. 예약했으면 비어 있다
     */
    public record Result(StockReservation reservation, List<UUID> insufficientSkuIds) {

        public boolean reserved() {
            return insufficientSkuIds.isEmpty();
        }
    }

    /** 확정·해제 결과. */
    public enum Transition {
        /** 바꿨다. */
        APPLIED,
        /** 이미 그 상태다. 아무것도 하지 않았다. */
        ALREADY,
        /** 지금 상태에서는 할 수 없다. 부르는 쪽이 경고 로그를 남기고 넘긴다. */
        INVALID
    }
}
