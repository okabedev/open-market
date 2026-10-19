package com.openmarket.catalog.command.application;

import com.openmarket.catalog.command.domain.Product;
import com.openmarket.catalog.command.domain.ProductNotFoundException;
import com.openmarket.catalog.command.domain.ProductRepository;
import com.openmarket.catalog.command.domain.ProductResumed;
import com.openmarket.catalog.command.domain.SellerRepository;
import com.openmarket.common.messaging.outbox.Outbox;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자가 직접 멈춘 상품의 판매를 다시 연다(CAT-2). {@code ProductResumed}를 낸다. 판매자가 정지 중이거나 제재된 상품이면 422다. */
@Service
public class ResumeProductUseCase {

    private final ProductRepository products;
    private final SellerRepository sellers;
    private final Outbox outbox;
    private final Clock clock;

    public ResumeProductUseCase(
            ProductRepository products, SellerRepository sellers, Outbox outbox, Clock clock) {
        this.products = products;
        this.sellers = sellers;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(ChangeSaleStatusCommand command) {
        Product product =
                products.findForUpdateById(command.productId())
                        .filter(found -> found.isOwnedBy(command.sellerId()))
                        .orElseThrow(ProductNotFoundException::new);
        RegisterProductUseCase.requireActiveSeller(sellers, command.sellerId());
        if (product.resume(clock.instant())) {
            products.save(product);
            publishResumed(outbox, product);
        }
    }

    static void publishResumed(Outbox outbox, Product product) {
        outbox.append(
                RegisterProductUseCase.TOPIC,
                product.id().toString(),
                ProductResumed.TYPE,
                new ProductResumed(product.id(), product.nextRevision()));
    }
}
