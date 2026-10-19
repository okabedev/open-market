package com.openmarket.catalog.command.application;

import com.openmarket.catalog.command.domain.CategoryRepository;
import com.openmarket.catalog.command.domain.InvalidProductException;
import com.openmarket.catalog.command.domain.Product;
import com.openmarket.catalog.command.domain.ProductRepository;
import com.openmarket.catalog.command.domain.ProductSnapshot;
import com.openmarket.catalog.command.domain.ProductSnapshotFactory;
import com.openmarket.catalog.command.domain.ProductStateException;
import com.openmarket.catalog.command.domain.Seller;
import com.openmarket.catalog.command.domain.SellerNotReadyException;
import com.openmarket.catalog.command.domain.SellerRepository;
import com.openmarket.catalog.command.domain.Sku;
import com.openmarket.catalog.command.domain.SkuInput;
import com.openmarket.catalog.command.domain.Stock;
import com.openmarket.catalog.command.domain.StockRepository;
import com.openmarket.common.core.id.Ids;
import com.openmarket.common.messaging.outbox.Outbox;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품을 {@code DRAFT}로 등록하고 SKU별 재고를 초기 수량으로 만든다(CAT-1). {@code ProductRegistered}를 낸다(file이 이미지 첨부를
 * 확정한다).
 */
@Service
public class RegisterProductUseCase {

    static final String TOPIC = "catalog.product";

    private final ProductRepository products;
    private final StockRepository stocks;
    private final CategoryRepository categories;
    private final SellerRepository sellers;
    private final ProductSnapshotFactory snapshots;
    private final Outbox outbox;
    private final Clock clock;

    public RegisterProductUseCase(
            ProductRepository products,
            StockRepository stocks,
            CategoryRepository categories,
            SellerRepository sellers,
            ProductSnapshotFactory snapshots,
            Outbox outbox,
            Clock clock) {
        this.products = products;
        this.stocks = stocks;
        this.categories = categories;
        this.sellers = sellers;
        this.snapshots = snapshots;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public UUID execute(RegisterProductCommand command) {
        requireActiveSeller(sellers, command.sellerId());
        checkLeafCategory(categories, command.categoryId());
        Instant now = clock.instant();
        // ID를 정해 만든 엔티티라 save가 merge로 저장하고 관리되는 사본을 돌려준다. 이후 변경(revision)은 그 사본에 한다.
        Product product =
                products.save(
                        Product.register(
                                Ids.newId(),
                                command.sellerId(),
                                command.categoryId(),
                                new Product.Details(
                                        command.name(),
                                        command.description(),
                                        command.imageFileIds()),
                                command.optionNames(),
                                command.skus().stream()
                                        .map(
                                                sku ->
                                                        new SkuInput(
                                                                null,
                                                                sku.optionValues(),
                                                                sku.price()))
                                        .toList(),
                                now));
        List<Sku> skus = product.skus();
        for (int i = 0; i < skus.size(); i++) {
            stocks.save(
                    Stock.create(
                            skus.get(i).id(),
                            product.id(),
                            command.skus().get(i).initialStock(),
                            now));
        }
        publish(outbox, ProductSnapshot.REGISTERED, snapshots.next(product));
        return product.id();
    }

    /** 판매자 사본이 있고 정지 중이 아닌지 본다. */
    static void requireActiveSeller(SellerRepository sellers, UUID sellerId) {
        Seller seller = sellers.findById(sellerId).orElseThrow(SellerNotReadyException::new);
        if (!seller.isActive()) {
            throw ProductStateException.sellerSuspended();
        }
    }

    /** 카테고리가 있고 소분류(자식이 없는 카테고리)인지 본다. */
    static void checkLeafCategory(CategoryRepository categories, UUID categoryId) {
        if (categories.findById(categoryId).isEmpty() || categories.existsByParentId(categoryId)) {
            throw new InvalidProductException("소분류 카테고리를 골라 주세요.");
        }
    }

    static void publish(Outbox outbox, String type, ProductSnapshot snapshot) {
        outbox.append(TOPIC, snapshot.productId().toString(), type, snapshot);
    }
}
