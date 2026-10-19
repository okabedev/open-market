package com.openmarket.catalog.command.application;

import com.openmarket.catalog.command.domain.Product;
import com.openmarket.catalog.command.domain.ProductRepository;
import com.openmarket.catalog.command.domain.Seller;
import com.openmarket.catalog.command.domain.SellerRepository;
import com.openmarket.common.messaging.outbox.Outbox;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자 정지 해제를 반영한다. 판매자 사본을 판매 중으로 바꾸고, 정지 때문에 멈춘 상품만 다시 연다. 판매자가 직접 멈춘 상품과 제재된 상품은 그대로다. */
@Service
public class ReinstateSellerUseCase {

    private final SellerRepository sellers;
    private final ProductRepository products;
    private final Outbox outbox;
    private final Clock clock;

    public ReinstateSellerUseCase(
            SellerRepository sellers, ProductRepository products, Outbox outbox, Clock clock) {
        this.sellers = sellers;
        this.products = products;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(SellerStatusCommand command) {
        Optional<Seller> found = sellers.findById(command.sellerId());
        Instant now = clock.instant();
        if (found.isEmpty() || !found.get().reinstate(now)) {
            return;
        }
        sellers.save(found.get());
        for (Product product : products.findForUpdateBySellerId(command.sellerId())) {
            if (product.resumeAfterReinstatement(now)) {
                products.save(product);
                ResumeProductUseCase.publishResumed(outbox, product);
            }
        }
    }
}
