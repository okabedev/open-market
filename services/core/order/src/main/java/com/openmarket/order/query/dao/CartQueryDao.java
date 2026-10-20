package com.openmarket.order.query.dao;

import static com.openmarket.order.command.domain.QCartItem.cartItem;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 장바구니에 담은 줄(SKU, 수량). 상품 정보는 {@link CatalogSkuQueryDao}가 읽는다. */
@Repository
public class CartQueryDao {

    private final JPAQueryFactory query;

    public CartQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    /** 최근에 담은 순. 장바구니가 없으면 빈 목록. */
    public List<Line> findLines(UUID memberId) {
        return query
                .select(cartItem.skuId, cartItem.quantity, cartItem.addedAt)
                .from(cartItem)
                .where(cartItem.cart.memberId.eq(memberId))
                .orderBy(cartItem.addedAt.desc(), cartItem.skuId.asc())
                .fetch()
                .stream()
                .map(
                        t ->
                                new Line(
                                        Objects.requireNonNull(t.get(cartItem.skuId)),
                                        Objects.requireNonNull(t.get(cartItem.quantity)),
                                        Objects.requireNonNull(t.get(cartItem.addedAt))))
                .toList();
    }

    public record Line(UUID skuId, int quantity, Instant addedAt) {}
}
