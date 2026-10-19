package com.openmarket.catalog.command.application;

import com.openmarket.catalog.command.domain.Product;
import com.openmarket.catalog.command.domain.ProductAvailabilityCalculator;
import com.openmarket.catalog.command.domain.ProductNotFoundException;
import com.openmarket.catalog.command.domain.ProductRepository;
import com.openmarket.catalog.command.domain.SkuNotFoundException;
import com.openmarket.catalog.command.domain.Stock;
import com.openmarket.catalog.command.domain.StockAdjustment;
import com.openmarket.catalog.command.domain.StockAdjustmentRepository;
import com.openmarket.catalog.command.domain.StockAvailabilityChanged;
import com.openmarket.catalog.command.domain.StockRepository;
import com.openmarket.common.core.id.Ids;
import com.openmarket.common.messaging.outbox.Outbox;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자가 SKU 실재고를 증감 수량만큼 바꾸고 이력을 남긴다(CAT-3). 재고 행을 잠그고 하므로 동시에 조정해도 서로 덮어쓰지 않는다. 상품 단위 판매 가능 여부가 바뀌면
 * {@code StockAvailabilityChanged}가 나간다. 상품 행은 잠그지 않는다.
 */
@Service
public class AdjustStockUseCase {

    static final String TOPIC = "catalog.stock";

    private final ProductRepository products;
    private final StockRepository stocks;
    private final StockAdjustmentRepository adjustments;
    private final ProductAvailabilityCalculator availability;
    private final Outbox outbox;
    private final Clock clock;

    public AdjustStockUseCase(
            ProductRepository products,
            StockRepository stocks,
            StockAdjustmentRepository adjustments,
            ProductAvailabilityCalculator availability,
            Outbox outbox,
            Clock clock) {
        this.products = products;
        this.stocks = stocks;
        this.adjustments = adjustments;
        this.availability = availability;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public Result execute(AdjustStockCommand command) {
        Product product =
                products.findById(command.productId())
                        .filter(found -> found.isOwnedBy(command.sellerId()))
                        .orElseThrow(ProductNotFoundException::new);
        product.checkStockAdjustable();
        StockAdjustment.normalizeReason(command.reason());
        Stock stock =
                stocks.findForUpdateBySkuId(command.skuId())
                        .filter(found -> found.productId().equals(product.id()))
                        .orElseThrow(SkuNotFoundException::new);
        Instant now = clock.instant();
        stock.adjust(command.delta(), now);
        stocks.save(stock);
        StockAdjustment adjustment =
                adjustments.save(
                        StockAdjustment.record(
                                Ids.newId(), stock, command.delta(), command.reason(), now));
        publishAvailability(outbox, availability.refresh(product.id(), now));
        return new Result(adjustment.id(), stock.onHand(), stock.reserved(), stock.available());
    }

    static void publishAvailability(Outbox outbox, Optional<StockAvailabilityChanged> changed) {
        changed.ifPresent(
                event ->
                        outbox.append(
                                TOPIC,
                                event.productId().toString(),
                                StockAvailabilityChanged.TYPE,
                                event));
    }

    /**
     * 조정 결과.
     *
     * @param onHand 조정 뒤 실재고
     * @param reserved 주문으로 예약된 수량
     * @param available 판매 가능 수량
     */
    public record Result(UUID adjustmentId, int onHand, int reserved, int available) {}
}
