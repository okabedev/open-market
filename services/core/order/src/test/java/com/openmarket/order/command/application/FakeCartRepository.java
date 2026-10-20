package com.openmarket.order.command.application;

import com.openmarket.order.command.domain.Cart;
import com.openmarket.order.command.domain.CartRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 메모리 저장소. 잠금 조회를 했는지 센다. */
class FakeCartRepository implements CartRepository {

    final Map<UUID, Cart> carts = new HashMap<>();
    int lockedReads;

    @Override
    public Cart save(Cart cart) {
        carts.put(cart.memberId(), cart);
        return cart;
    }

    @Override
    public void createIfAbsent(UUID memberId) {
        carts.putIfAbsent(memberId, Cart.create(memberId, Instant.EPOCH));
    }

    @Override
    public Optional<Cart> findForUpdateByMemberId(UUID memberId) {
        lockedReads++;
        return Optional.ofNullable(carts.get(memberId));
    }
}
