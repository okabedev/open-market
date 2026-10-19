package com.openmarket.catalog.query.dao;

import static com.openmarket.catalog.command.domain.QProduct.product;
import static com.openmarket.catalog.command.domain.QSeller.seller;
import static com.openmarket.catalog.command.domain.QSku.sku;
import static com.openmarket.catalog.command.domain.QStock.stock;

import com.openmarket.catalog.query.dto.AdminProductSummaryView;
import com.openmarket.catalog.query.dto.PageView;
import com.openmarket.catalog.query.dto.ProductDetailView;
import com.openmarket.catalog.query.dto.ProductStatus;
import com.openmarket.catalog.query.dto.SellerProductSummaryView;
import com.openmarket.catalog.query.dto.SellerProductView;
import com.openmarket.catalog.query.dto.StopReason;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/** 상품 조회. 상품 한 건은 행, 이미지, SKU·재고를 따로 읽는다(SKU 100개, 이미지 10장 상한). */
@Repository
public class ProductQueryDao {

    /** 구매자에게 보이는 상태. 판매 중지 상품은 상세에서 "판매 중지"로 보인다. */
    private static final List<String> PUBLIC_STATUSES =
            List.of(ProductStatus.ON_SALE.name(), ProductStatus.STOPPED.name());

    private static final ComparablePath<UUID> IMAGE =
            Expressions.comparablePath(UUID.class, "image");
    private static final NumberExpression<Integer> IMAGE_INDEX =
            Expressions.numberTemplate(Integer.class, "index({0})", IMAGE);

    /** SKU들의 판매 가능 수량 합. 재고 행이 없으면 null이라 0으로 읽는다. */
    private static final NumberExpression<Long> AVAILABLE_SUM =
            Expressions.numberTemplate(Long.class, "sum({0} - {1})", stock.onHand, stock.reserved);

    private final JPAQueryFactory query;
    private final CategoryQueryDao categories;

    public ProductQueryDao(JPAQueryFactory query, CategoryQueryDao categories) {
        this.query = query;
        this.categories = categories;
    }

    /** 구매자 상세. 판매 중이거나 판매 중지된 상품만. */
    public Optional<ProductDetailView> findPublic(UUID productId) {
        Tuple row =
                query.select(
                                product.id,
                                product.status.stringValue(),
                                product.name,
                                product.description,
                                product.categoryId,
                                product.sellerId,
                                seller.storeName,
                                product.optionName1,
                                product.optionName2,
                                product.publishedAt)
                        .from(product)
                        .join(seller)
                        .on(seller.id.eq(product.sellerId))
                        .where(
                                product.id.eq(productId),
                                product.status.stringValue().in(PUBLIC_STATUSES))
                        .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        List<ProductDetailView.Sku> skus =
                skuRows(productId).stream()
                        .map(
                                s ->
                                        new ProductDetailView.Sku(
                                                s.skuId(),
                                                s.optionValues(),
                                                s.price(),
                                                s.onHand() - s.reserved() > 0))
                        .toList();
        return Optional.of(
                new ProductDetailView(
                        productId,
                        ProductStatus.valueOf(Objects.requireNonNull(row.get(1, String.class))),
                        Objects.requireNonNull(row.get(product.name)),
                        Objects.requireNonNull(row.get(product.description)),
                        categories.findPath(Objects.requireNonNull(row.get(product.categoryId))),
                        Objects.requireNonNull(row.get(product.sellerId)),
                        Objects.requireNonNull(row.get(seller.storeName)),
                        images(productId),
                        optionNames(row.get(product.optionName1), row.get(product.optionName2)),
                        skus,
                        skus.stream().mapToLong(ProductDetailView.Sku::price).min().orElse(0),
                        skus.stream().anyMatch(ProductDetailView.Sku::inStock),
                        row.get(product.publishedAt)));
    }

    /** 판매자 본인 상세. 상태와 상관없이 보인다. 다른 판매자의 상품이면 비어 있다. */
    public Optional<SellerProductView> findForSeller(UUID sellerId, UUID productId) {
        Tuple row =
                query.select(
                                product.status.stringValue(),
                                product.stopReason.stringValue(),
                                product.banReason,
                                product.name,
                                product.description,
                                product.categoryId,
                                product.optionName1,
                                product.optionName2,
                                product.publishedAt,
                                product.createdAt,
                                product.updatedAt)
                        .from(product)
                        .where(product.id.eq(productId), product.sellerId.eq(sellerId))
                        .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(
                new SellerProductView(
                        productId,
                        ProductStatus.valueOf(Objects.requireNonNull(row.get(0, String.class))),
                        stopReason(row.get(1, String.class)),
                        row.get(product.banReason),
                        Objects.requireNonNull(row.get(product.name)),
                        Objects.requireNonNull(row.get(product.description)),
                        categories.findPath(Objects.requireNonNull(row.get(product.categoryId))),
                        images(productId),
                        optionNames(row.get(product.optionName1), row.get(product.optionName2)),
                        skuRows(productId).stream()
                                .map(
                                        s ->
                                                new SellerProductView.Sku(
                                                        s.skuId(),
                                                        s.optionValues(),
                                                        s.price(),
                                                        s.onHand(),
                                                        s.reserved(),
                                                        s.onHand() - s.reserved()))
                                .toList(),
                        row.get(product.publishedAt),
                        Objects.requireNonNull(row.get(product.createdAt)),
                        Objects.requireNonNull(row.get(product.updatedAt))));
    }

    /** 판매자 상품 목록. 최근 수정 순. {@code status}가 null이면 모든 상태. */
    public PageView<SellerProductSummaryView> findSellerProducts(
            UUID sellerId, @Nullable ProductStatus status, int page, int size) {
        BooleanBuilder where = new BooleanBuilder(product.sellerId.eq(sellerId));
        if (status != null) {
            where.and(product.status.stringValue().eq(status.name()));
        }
        long total =
                Objects.requireNonNullElse(
                        query.select(product.count()).from(product).where(where).fetchOne(), 0L);
        List<Tuple> rows =
                query.select(
                                product.id,
                                product.name,
                                product.status.stringValue(),
                                product.updatedAt)
                        .from(product)
                        .where(where)
                        .orderBy(product.updatedAt.desc(), product.id.desc())
                        .offset((long) page * size)
                        .limit(size)
                        .fetch();
        List<UUID> ids = rows.stream().map(r -> Objects.requireNonNull(r.get(product.id))).toList();
        Map<UUID, Tuple> totals = skuTotals(ids);
        Map<UUID, UUID> thumbnails = thumbnails(ids);
        List<SellerProductSummaryView> items = new ArrayList<>();
        for (Tuple r : rows) {
            UUID id = Objects.requireNonNull(r.get(product.id));
            @Nullable Tuple t = totals.get(id);
            items.add(
                    new SellerProductSummaryView(
                            id,
                            Objects.requireNonNull(r.get(product.name)),
                            ProductStatus.valueOf(Objects.requireNonNull(r.get(2, String.class))),
                            thumbnails.get(id),
                            t == null ? 0 : Objects.requireNonNullElse(t.get(1, Long.class), 0L),
                            t == null
                                    ? 0
                                    : Objects.requireNonNullElse(t.get(2, Long.class), 0L)
                                            .intValue(),
                            t == null ? 0 : Objects.requireNonNullElse(t.get(3, Long.class), 0L),
                            Objects.requireNonNull(r.get(product.updatedAt))));
        }
        return new PageView<>(items, total, page, size);
    }

    /** 관리자 상품 목록. 최근 수정 순. 등록 중인 상품은 빼지 않는다(제재할 수 있다). */
    public PageView<AdminProductSummaryView> findAdminProducts(
            @Nullable ProductStatus status, @Nullable UUID sellerId, int page, int size) {
        BooleanBuilder where = new BooleanBuilder();
        if (status != null) {
            where.and(product.status.stringValue().eq(status.name()));
        }
        if (sellerId != null) {
            where.and(product.sellerId.eq(sellerId));
        }
        long total =
                Objects.requireNonNullElse(
                        query.select(product.count()).from(product).where(where).fetchOne(), 0L);
        List<Tuple> rows =
                query.select(
                                product.id,
                                product.name,
                                product.status.stringValue(),
                                product.sellerId,
                                seller.storeName,
                                product.banReason,
                                product.updatedAt)
                        .from(product)
                        .join(seller)
                        .on(seller.id.eq(product.sellerId))
                        .where(where)
                        .orderBy(product.updatedAt.desc(), product.id.desc())
                        .offset((long) page * size)
                        .limit(size)
                        .fetch();
        List<UUID> ids = rows.stream().map(r -> Objects.requireNonNull(r.get(product.id))).toList();
        Map<UUID, Tuple> totals = skuTotals(ids);
        Map<UUID, UUID> thumbnails = thumbnails(ids);
        List<AdminProductSummaryView> items = new ArrayList<>();
        for (Tuple r : rows) {
            UUID id = Objects.requireNonNull(r.get(product.id));
            @Nullable Tuple t = totals.get(id);
            items.add(
                    new AdminProductSummaryView(
                            id,
                            Objects.requireNonNull(r.get(product.name)),
                            ProductStatus.valueOf(Objects.requireNonNull(r.get(2, String.class))),
                            Objects.requireNonNull(r.get(product.sellerId)),
                            Objects.requireNonNull(r.get(seller.storeName)),
                            thumbnails.get(id),
                            t == null ? 0 : Objects.requireNonNullElse(t.get(1, Long.class), 0L),
                            r.get(product.banReason),
                            Objects.requireNonNull(r.get(product.updatedAt))));
        }
        return new PageView<>(items, total, page, size);
    }

    private static @Nullable StopReason stopReason(@Nullable String name) {
        return name == null ? null : StopReason.valueOf(name);
    }

    private List<UUID> images(UUID productId) {
        return query.select(IMAGE)
                .from(product)
                .join(product.imageFileIds, IMAGE)
                .where(product.id.eq(productId))
                .orderBy(IMAGE_INDEX.asc())
                .fetch();
    }

    private Map<UUID, UUID> thumbnails(List<UUID> productIds) {
        Map<UUID, UUID> result = new HashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        query.select(product.id, IMAGE)
                .from(product)
                .join(product.imageFileIds, IMAGE)
                .where(product.id.in(productIds), IMAGE_INDEX.eq(0))
                .fetch()
                .forEach(
                        t ->
                                result.put(
                                        Objects.requireNonNull(t.get(product.id)),
                                        Objects.requireNonNull(t.get(IMAGE))));
        return result;
    }

    /** 상품별 최저가(1), SKU 수(2), 판매 가능 수량 합(3). */
    private Map<UUID, Tuple> skuTotals(List<UUID> productIds) {
        Map<UUID, Tuple> result = new HashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        query.select(sku.product.id, sku.price.min(), sku.count(), AVAILABLE_SUM)
                .from(sku)
                .leftJoin(stock)
                .on(stock.skuId.eq(sku.id))
                .where(sku.product.id.in(productIds))
                .groupBy(sku.product.id)
                .fetch()
                .forEach(t -> result.put(Objects.requireNonNull(t.get(0, UUID.class)), t));
        return result;
    }

    private List<SkuRow> skuRows(UUID productId) {
        return query
                .select(
                        sku.id,
                        sku.optionValue1,
                        sku.optionValue2,
                        sku.price,
                        stock.onHand,
                        stock.reserved)
                .from(sku)
                .leftJoin(stock)
                .on(stock.skuId.eq(sku.id))
                .where(sku.product.id.eq(productId))
                .orderBy(sku.position.asc())
                .fetch()
                .stream()
                .map(
                        t ->
                                new SkuRow(
                                        Objects.requireNonNull(t.get(sku.id)),
                                        optionNames(
                                                t.get(sku.optionValue1), t.get(sku.optionValue2)),
                                        Objects.requireNonNull(t.get(sku.price)),
                                        Objects.requireNonNullElse(t.get(stock.onHand), 0),
                                        Objects.requireNonNullElse(t.get(stock.reserved), 0)))
                .toList();
    }

    /** 컬럼 두 개에 나눠 둔 옵션 이름·값을 목록으로. */
    private static List<String> optionNames(@Nullable String first, @Nullable String second) {
        List<String> values = new ArrayList<>(2);
        if (first != null) {
            values.add(first);
        }
        if (second != null) {
            values.add(second);
        }
        return List.copyOf(values);
    }

    private record SkuRow(
            UUID skuId, List<String> optionValues, long price, int onHand, int reserved) {}
}
