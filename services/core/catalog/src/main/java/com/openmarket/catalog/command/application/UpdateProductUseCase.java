package com.openmarket.catalog.command.application;

import com.openmarket.catalog.command.domain.CategoryRepository;
import com.openmarket.catalog.command.domain.Product;
import com.openmarket.catalog.command.domain.ProductNotFoundException;
import com.openmarket.catalog.command.domain.ProductRepository;
import com.openmarket.catalog.command.domain.ProductSnapshot;
import com.openmarket.catalog.command.domain.ProductSnapshotFactory;
import com.openmarket.catalog.command.domain.Sku;
import com.openmarket.catalog.command.domain.SkuInput;
import com.openmarket.catalog.command.domain.Stock;
import com.openmarket.catalog.command.domain.StockRepository;
import com.openmarket.common.messaging.outbox.Outbox;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 내용과 SKU 가격을 고치고 새 SKU를 더한다(CAT-2). 상태와 상관없이 {@code ProductChanged}를 낸다. 검색 반영은 search가 상태를 보고
 * 정한다. 다른 판매자의 상품은 없는 상품처럼 404다.
 */
@Service
public class UpdateProductUseCase {

    private final ProductRepository products;
    private final StockRepository stocks;
    private final CategoryRepository categories;
    private final ProductSnapshotFactory snapshots;
    private final Outbox outbox;
    private final Clock clock;

    public UpdateProductUseCase(
            ProductRepository products,
            StockRepository stocks,
            CategoryRepository categories,
            ProductSnapshotFactory snapshots,
            Outbox outbox,
            Clock clock) {
        this.products = products;
        this.stocks = stocks;
        this.categories = categories;
        this.snapshots = snapshots;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(UpdateProductCommand command) {
        Product product =
                products.findForUpdateById(command.productId())
                        .filter(found -> found.isOwnedBy(command.sellerId()))
                        .orElseThrow(ProductNotFoundException::new);
        RegisterProductUseCase.checkLeafCategory(categories, command.categoryId());
        Instant now = clock.instant();
        List<Sku> added =
                product.update(
                        command.categoryId(),
                        new Product.Details(
                                command.name(), command.description(), command.imageFileIds()),
                        command.skus().stream()
                                .map(
                                        sku ->
                                                new SkuInput(
                                                        sku.skuId(),
                                                        sku.optionValues(),
                                                        sku.price()))
                                .toList(),
                        now);
        // 새 SKU의 INSERT를 재고보다 먼저 예약한다(stock.sku_id 외래 키).
        products.save(product);
        List<UpdateProductCommand.Sku> newLines =
                command.skus().stream().filter(sku -> sku.skuId() == null).toList();
        for (int i = 0; i < added.size(); i++) {
            stocks.save(
                    Stock.create(
                            added.get(i).id(), product.id(), newLines.get(i).initialStock(), now));
        }
        RegisterProductUseCase.publish(outbox, ProductSnapshot.CHANGED, snapshots.next(product));
    }
}
