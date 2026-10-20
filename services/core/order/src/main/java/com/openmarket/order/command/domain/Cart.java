package com.openmarket.order.command.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 장바구니(docs/glossary.md "장바구니"). 회원당 하나다. 줄은 SKU와 수량만 갖고, 상품 정보는 볼 때 catalog에서 읽는다
 * (docs/architecture/order.md "장바구니").
 */
@Entity
@Table(name = "cart")
public class Cart {

    /** 담을 수 있는 SKU 수. */
    public static final int MAX_ITEMS = 50;

    @Id
    @Column(name = "member_id")
    private UUID memberId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Cart() {}

    private Cart(UUID memberId, Instant now) {
        this.memberId = memberId;
        this.updatedAt = now;
    }

    public static Cart create(UUID memberId, Instant now) {
        return new Cart(memberId, now);
    }

    /**
     * SKU를 담는다. 이미 담은 SKU면 수량을 더하고 담은 시각을 새로 한다.
     *
     * @throws InvalidCartInputException 수량이 1~99가 아니다
     * @throws CartQuantityExceededException 더한 수량이 99를 넘는다
     * @throws CartFullException 새 SKU인데 이미 50개를 담았다
     */
    public void add(UUID skuId, int quantity, Instant now) {
        CartItem.checkQuantity(quantity);
        Optional<CartItem> existing = find(skuId);
        if (existing.isPresent()) {
            existing.get().accumulate(quantity, now);
        } else {
            if (items.size() >= MAX_ITEMS) {
                throw new CartFullException();
            }
            items.add(CartItem.create(this, skuId, quantity, now));
        }
        updatedAt = now;
    }

    /**
     * 담은 SKU의 수량을 바꾼다.
     *
     * @throws InvalidCartInputException 수량이 1~99가 아니다
     * @throws CartItemNotFoundException 담지 않은 SKU다
     */
    public void changeQuantity(UUID skuId, int quantity, Instant now) {
        CartItem.checkQuantity(quantity);
        find(skuId).orElseThrow(CartItemNotFoundException::new).changeQuantity(quantity);
        updatedAt = now;
    }

    /** SKU를 뺀다. 담지 않은 SKU면 아무것도 하지 않는다. */
    public void remove(UUID skuId, Instant now) {
        if (items.removeIf(item -> item.skuId().equals(skuId))) {
            updatedAt = now;
        }
    }

    public UUID memberId() {
        return memberId;
    }

    public List<CartItem> items() {
        return Collections.unmodifiableList(items);
    }

    private Optional<CartItem> find(UUID skuId) {
        return items.stream().filter(item -> item.skuId().equals(skuId)).findFirst();
    }
}
