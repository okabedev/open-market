package com.openmarket.catalog.command.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * 상품 이벤트의 스냅샷을 만든다. revision을 하나 올리므로 이벤트를 낼 때만 부른다. 상호는 판매자 사본, 카테고리 경로는 카테고리, SKU별 재고 여부는 재고, 상품
 * 단위 재고 여부와 그 리비전은 판매 가능 여부에서 읽는다. 같은 트랜잭션에서 방금 만들거나 바꾼 값도 저장소에서 읽힌다.
 */
public class ProductSnapshotFactory {

    private final CategoryRepository categories;
    private final SellerRepository sellers;
    private final StockRepository stocks;
    private final ProductAvailabilityRepository availabilities;

    public ProductSnapshotFactory(
            CategoryRepository categories,
            SellerRepository sellers,
            StockRepository stocks,
            ProductAvailabilityRepository availabilities) {
        this.categories = categories;
        this.sellers = sellers;
        this.stocks = stocks;
        this.availabilities = availabilities;
    }

    public ProductSnapshot next(Product product) {
        Seller seller =
                sellers.findById(product.sellerId()).orElseThrow(SellerNotReadyException::new);
        Map<UUID, Stock> stockBySku =
                stocks.findByProductId(product.id()).stream()
                        .collect(Collectors.toMap(Stock::skuId, Function.identity()));
        List<ProductSnapshot.SkuSnapshot> skus =
                product.skus().stream()
                        .map(
                                sku -> {
                                    @Nullable Stock stock = stockBySku.get(sku.id());
                                    return new ProductSnapshot.SkuSnapshot(
                                            sku.id(),
                                            sku.optionValues(),
                                            sku.price(),
                                            stock != null && stock.inStock());
                                })
                        .toList();
        ProductAvailability availability =
                availabilities
                        .findByProductId(product.id())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "판매 가능 여부가 없는 상품: " + product.id()));
        return new ProductSnapshot(
                product.id(),
                product.nextRevision(),
                product.status().name(),
                product.sellerId(),
                seller.storeName(),
                product.categoryId(),
                categoryPath(product.categoryId()),
                product.name(),
                product.description(),
                product.imageFileIds(),
                product.optionNames(),
                skus,
                product.minPrice(),
                availability.inStock(),
                availability.revision(),
                product.publishedAt());
    }

    private List<UUID> categoryPath(UUID categoryId) {
        List<UUID> path = new ArrayList<>();
        @Nullable UUID current = categoryId;
        while (current != null) {
            path.add(current);
            current = categories.findById(current).map(Category::parentId).orElse(null);
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }
}
