package com.openmarket.catalog.query.dao;

import static com.openmarket.catalog.command.domain.QProduct.product;
import static com.openmarket.catalog.command.domain.QSku.sku;
import static com.openmarket.catalog.command.domain.QStockAdjustment.stockAdjustment;

import com.openmarket.catalog.query.dto.PageView;
import com.openmarket.catalog.query.dto.StockAdjustmentView;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 재고 조정 이력 조회. */
@Repository
public class StockAdjustmentQueryDao {

    private final JPAQueryFactory query;

    public StockAdjustmentQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    public boolean isOwnedBy(UUID sellerId, UUID productId) {
        return query.selectOne()
                        .from(product)
                        .where(product.id.eq(productId), product.sellerId.eq(sellerId))
                        .fetchFirst()
                != null;
    }

    public boolean skuBelongsTo(UUID productId, UUID skuId) {
        return query.selectOne()
                        .from(sku)
                        .where(sku.id.eq(skuId), sku.product.id.eq(productId))
                        .fetchFirst()
                != null;
    }

    /** SKU 하나의 이력. 최근 순. */
    public PageView<StockAdjustmentView> findBySku(UUID skuId, int page, int size) {
        long total =
                Objects.requireNonNullElse(
                        query.select(stockAdjustment.count())
                                .from(stockAdjustment)
                                .where(stockAdjustment.skuId.eq(skuId))
                                .fetchOne(),
                        0L);
        List<StockAdjustmentView> items =
                query.select(
                                Projections.constructor(
                                        StockAdjustmentView.class,
                                        stockAdjustment.id,
                                        stockAdjustment.delta,
                                        stockAdjustment.onHandAfter,
                                        stockAdjustment.reason,
                                        stockAdjustment.createdAt))
                        .from(stockAdjustment)
                        .where(stockAdjustment.skuId.eq(skuId))
                        // UUID v7이라 같은 시각이면 ID가 나중에 만든 순서다.
                        .orderBy(stockAdjustment.createdAt.desc(), stockAdjustment.id.desc())
                        .offset((long) page * size)
                        .limit(size)
                        .fetch();
        return new PageView<>(items, total, page, size);
    }
}
