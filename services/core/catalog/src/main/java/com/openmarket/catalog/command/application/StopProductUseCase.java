package com.openmarket.catalog.command.application;

import com.openmarket.catalog.command.domain.Product;
import com.openmarket.catalog.command.domain.ProductNotFoundException;
import com.openmarket.catalog.command.domain.ProductRepository;
import com.openmarket.catalog.command.domain.ProductStopped;
import com.openmarket.catalog.command.domain.StopReason;
import com.openmarket.common.messaging.outbox.Outbox;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자가 판매를 멈춘다(CAT-2). {@code ProductStopped}를 낸다. 이미 멈췄으면 아무것도 하지 않는다. */
@Service
public class StopProductUseCase {

    private final ProductRepository products;
    private final Outbox outbox;
    private final Clock clock;

    public StopProductUseCase(ProductRepository products, Outbox outbox, Clock clock) {
        this.products = products;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(ChangeSaleStatusCommand command) {
        Product product =
                products.findForUpdateById(command.productId())
                        .filter(found -> found.isOwnedBy(command.sellerId()))
                        .orElseThrow(ProductNotFoundException::new);
        if (product.stop(StopReason.SELLER, clock.instant())) {
            products.save(product);
            publishStopped(outbox, product, StopReason.SELLER);
        }
    }

    static void publishStopped(Outbox outbox, Product product, StopReason reason) {
        outbox.append(
                RegisterProductUseCase.TOPIC,
                product.id().toString(),
                ProductStopped.TYPE,
                new ProductStopped(product.id(), product.nextRevision(), reason.name()));
    }
}
