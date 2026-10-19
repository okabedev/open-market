package com.openmarket.catalog.command.application;

import com.openmarket.catalog.command.domain.Product;
import com.openmarket.catalog.command.domain.ProductRepository;
import com.openmarket.catalog.command.domain.Seller;
import com.openmarket.catalog.command.domain.SellerRepository;
import com.openmarket.catalog.command.domain.StopReason;
import com.openmarket.common.messaging.outbox.Outbox;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 정지(SEL-3)를 반영한다. 판매자 사본을 정지로 바꾸고 판매 중인 상품을 모두 멈춘다(사유 {@code SELLER_SUSPENDED}). 이미 정지했거나 사본이
 * 없으면 아무것도 하지 않는다.
 *
 * <p>상품을 한 트랜잭션에서 모두 잠근다. 판매자 한 명의 상품이 수천 개가 되면 나눠 처리하는 방법을 M6 부하 테스트에서 검토한다.
 */
@Service
public class SuspendSellerUseCase {

    private final SellerRepository sellers;
    private final ProductRepository products;
    private final Outbox outbox;
    private final Clock clock;

    public SuspendSellerUseCase(
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
        if (found.isEmpty() || !found.get().suspend(now)) {
            return;
        }
        sellers.save(found.get());
        for (Product product : products.findForUpdateBySellerId(command.sellerId())) {
            if (product.stop(StopReason.SELLER_SUSPENDED, now)) {
                products.save(product);
                StopProductUseCase.publishStopped(outbox, product, StopReason.SELLER_SUSPENDED);
            }
        }
    }
}
