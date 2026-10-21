package com.openmarket.order.command.domain;

import com.openmarket.common.core.id.Ids;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 하위 주문(docs/glossary.md "하위 주문"). 주문을 판매자별로 나눈 단위다. {@link Order}에 딸린다. */
@Entity
@Table(name = "seller_order")
public class SellerOrder {

    @Id private UUID id;

    // Order.sellerOrders(mappedBy)의 주인 쪽이라 JPA가 order_id를 쓰는 데 쓴다. 코드에서는 읽지 않는다.
    @SuppressWarnings("UnusedVariable")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private int position;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "store_name", nullable = false)
    private String storeName;

    @Column(nullable = false)
    private long amount;

    // 결제 전에는 비어 있다. 결제 완료가 되면 PAID부터 앞으로만 간다.
    @Enumerated(EnumType.STRING)
    @Column
    private @Nullable SellerOrderProgress progress;

    @Column(name = "progress_updated_at")
    private @Nullable Instant progressUpdatedAt;

    @OneToMany(mappedBy = "sellerOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<OrderLine> lines = new ArrayList<>();

    protected SellerOrder() {}

    SellerOrder(Order order, int position, UUID sellerId, String storeName) {
        this.id = Ids.newId();
        this.order = order;
        this.position = position;
        this.sellerId = sellerId;
        this.storeName = storeName;
    }

    void add(OrderItem item) {
        OrderLine line = new OrderLine(this, lines.size(), item);
        lines.add(line);
        amount = Math.addExact(amount, line.amount());
    }

    /** 주문이 결제 완료가 됐다. */
    void startProgress(Instant now) {
        progress = SellerOrderProgress.PAID;
        progressUpdatedAt = now;
    }

    /** 진행 상태를 앞으로 바꾼다. 같거나 뒤의 단계면 아무것도 하지 않고 {@code false}. */
    boolean advance(SellerOrderProgress next, Instant now) {
        if (progress != null && next.compareTo(progress) <= 0) {
            return false;
        }
        progress = next;
        progressUpdatedAt = now;
        return true;
    }

    public UUID id() {
        return id;
    }

    public UUID sellerId() {
        return sellerId;
    }

    public long amount() {
        return amount;
    }

    public @Nullable SellerOrderProgress progress() {
        return progress;
    }

    public List<OrderLine> lines() {
        return Collections.unmodifiableList(lines);
    }
}
