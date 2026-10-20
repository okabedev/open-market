package com.openmarket.order.command.domain;

import com.openmarket.common.core.id.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 장바구니에 담은 줄. SKU 하나와 수량. {@link Cart}에 딸린다. */
@Entity
@Table(name = "cart_item")
public class CartItem {

    /** 한 줄의 최대 수량. */
    public static final int MAX_QUANTITY = 99;

    @Id private UUID id;

    // Cart.items(mappedBy)의 주인 쪽이라 JPA가 member_id를 쓰는 데 쓴다. 코드에서는 읽지 않는다.
    @SuppressWarnings("UnusedVariable")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Cart cart;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected CartItem() {}

    private CartItem(Cart cart, UUID skuId, int quantity, Instant now) {
        this.id = Ids.newId();
        this.cart = cart;
        this.skuId = skuId;
        this.quantity = quantity;
        this.addedAt = now;
    }

    static CartItem create(Cart cart, UUID skuId, int quantity, Instant now) {
        return new CartItem(cart, skuId, quantity, now);
    }

    public static void checkQuantity(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new InvalidOrderInputException("수량은 1부터 99까지입니다.");
        }
    }

    void accumulate(int added, Instant now) {
        if (quantity + added > MAX_QUANTITY) {
            throw new CartQuantityExceededException();
        }
        quantity += added;
        addedAt = now;
    }

    void changeQuantity(int quantity) {
        this.quantity = quantity;
    }

    public UUID skuId() {
        return skuId;
    }

    public int quantity() {
        return quantity;
    }

    public Instant addedAt() {
        return addedAt;
    }
}
