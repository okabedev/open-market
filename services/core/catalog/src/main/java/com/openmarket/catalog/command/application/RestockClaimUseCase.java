package com.openmarket.catalog.command.application;

import com.openmarket.catalog.command.domain.ProductAvailabilityCalculator;
import com.openmarket.catalog.command.domain.Stock;
import com.openmarket.catalog.command.domain.StockAdjustment;
import com.openmarket.catalog.command.domain.StockAdjustmentRepository;
import com.openmarket.catalog.command.domain.StockRepository;
import com.openmarket.common.core.id.Ids;
import com.openmarket.common.messaging.outbox.Outbox;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 끝난 클레임의 수량을 실재고에 더하고 재고 조정 이력을 클레임 ID와 함께 남긴다(docs/architecture/catalog.md "클레임 재입고"). 재고 행을
 * skuId 순서로 잠근다. 이미 재입고한 클레임이면 아무것도 하지 않는다. 판매 가능 여부가 바뀐 상품은 {@code StockAvailabilityChanged}를 낸다.
 */
@Service
public class RestockClaimUseCase {

    private static final Logger log = LoggerFactory.getLogger(RestockClaimUseCase.class);

    private final StockRepository stocks;
    private final StockAdjustmentRepository adjustments;
    private final ProductAvailabilityCalculator availability;
    private final Outbox outbox;
    private final Clock clock;

    public RestockClaimUseCase(
            StockRepository stocks,
            StockAdjustmentRepository adjustments,
            ProductAvailabilityCalculator availability,
            Outbox outbox,
            Clock clock) {
        this.stocks = stocks;
        this.adjustments = adjustments;
        this.availability = availability;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(RestockClaimCommand command) {
        if (command.lines().isEmpty() || adjustments.existsByClaimId(command.claimId())) {
            return;
        }
        Map<UUID, Integer> quantities = new TreeMap<>();
        command.lines()
                .forEach(line -> quantities.merge(line.skuId(), line.quantity(), Integer::sum));
        Instant now = clock.instant();
        TreeSet<UUID> productIds = new TreeSet<>();
        quantities.forEach(
                (skuId, quantity) -> {
                    Optional<Stock> found = stocks.findForUpdateBySkuId(skuId);
                    if (found.isEmpty()) {
                        log.warn(
                                "재고가 없는 SKU라 재입고를 넘긴다: claimId={}, skuId={}",
                                command.claimId(),
                                skuId);
                        return;
                    }
                    Stock stock = found.get();
                    int added = stock.restock(quantity, now);
                    if (added == 0) {
                        log.warn(
                                "실재고가 상한이라 재입고를 넘긴다: claimId={}, skuId={}",
                                command.claimId(),
                                skuId);
                        return;
                    }
                    stocks.save(stock);
                    adjustments.save(
                            StockAdjustment.restock(
                                    Ids.newId(), stock, added, command.claimId(), now));
                    productIds.add(stock.productId());
                });
        productIds.forEach(
                productId ->
                        AdjustStockUseCase.publishAvailability(
                                outbox, availability.refresh(productId, now)));
    }
}
