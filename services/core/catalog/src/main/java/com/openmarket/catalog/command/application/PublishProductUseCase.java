package com.openmarket.catalog.command.application;

import com.openmarket.catalog.command.domain.Product;
import com.openmarket.catalog.command.domain.ProductNotFoundException;
import com.openmarket.catalog.command.domain.ProductRepository;
import com.openmarket.catalog.command.domain.ProductSnapshot;
import com.openmarket.catalog.command.domain.ProductSnapshotFactory;
import com.openmarket.catalog.command.domain.SellerRepository;
import com.openmarket.common.messaging.outbox.Outbox;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code DRAFT} 상품의 판매를 시작하고 {@code ProductPublished}를 낸다. 이미 판매 중이면 아무것도 하지 않는다. 판매자가 정지 중이면 422
 * {@code CATALOG_SELLER_SUSPENDED}다.
 */
@Service
public class PublishProductUseCase {

    private final ProductRepository products;
    private final SellerRepository sellers;
    private final ProductSnapshotFactory snapshots;
    private final Outbox outbox;
    private final Clock clock;

    public PublishProductUseCase(
            ProductRepository products,
            SellerRepository sellers,
            ProductSnapshotFactory snapshots,
            Outbox outbox,
            Clock clock) {
        this.products = products;
        this.sellers = sellers;
        this.snapshots = snapshots;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(PublishProductCommand command) {
        Product product =
                products.findForUpdateById(command.productId())
                        .filter(found -> found.isOwnedBy(command.sellerId()))
                        .orElseThrow(ProductNotFoundException::new);
        RegisterProductUseCase.requireActiveSeller(sellers, command.sellerId());
        if (!product.publish(clock.instant())) {
            return;
        }
        products.save(product);
        RegisterProductUseCase.publish(outbox, ProductSnapshot.PUBLISHED, snapshots.next(product));
    }
}
