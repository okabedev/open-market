package com.openmarket.catalog.command.application;

import com.openmarket.catalog.command.domain.ProductAvailabilityCalculator;
import com.openmarket.catalog.command.domain.Stock;
import com.openmarket.catalog.command.domain.StockRepository;
import com.openmarket.catalog.command.domain.StockReservation;
import com.openmarket.catalog.command.domain.StockReservationFailed;
import com.openmarket.catalog.command.domain.StockReservationRepository;
import com.openmarket.catalog.command.domain.StockReserved;
import com.openmarket.common.messaging.outbox.Outbox;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문의 재고를 모두 예약하거나 하나도 예약하지 않는다(CAT-4, docs/architecture/catalog.md "재고 예약"). 재고 행을 skuId 순서로 잠그고
 * 판매 가능 수량을 확인하므로 동시에 들어온 주문이 재고를 넘겨 가져가지 못한다. 결과는 {@code StockReserved} 또는 {@code
 * StockReservationFailed}로 낸다.
 */
@Service
public class ReserveStockUseCase {

    private final StockReservationRepository reservations;
    private final StockRepository stocks;
    private final ProductAvailabilityCalculator availability;
    private final Outbox outbox;
    private final Clock clock;

    public ReserveStockUseCase(
            StockReservationRepository reservations,
            StockRepository stocks,
            ProductAvailabilityCalculator availability,
            Outbox outbox,
            Clock clock) {
        this.reservations = reservations;
        this.stocks = stocks;
        this.availability = availability;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void execute(ReserveStockCommand command) {
        // 주문마다 예약 행이 하나다. 이미 있으면(예약, 실패, 먼저 온 취소) 다시 예약하지 않는다.
        if (reservations.existsByOrderId(command.orderId())) {
            return;
        }
        Map<UUID, Integer> quantities = new TreeMap<>();
        command.lines()
                .forEach(line -> quantities.merge(line.skuId(), line.quantity(), Integer::sum));
        Map<UUID, Stock> locked = new HashMap<>();
        quantities
                .keySet()
                .forEach(
                        skuId ->
                                stocks.findForUpdateBySkuId(skuId)
                                        .ifPresent(stock -> locked.put(skuId, stock)));
        Instant now = clock.instant();
        StockReservation.Result result =
                StockReservation.reserve(command.orderId(), quantities, locked, now);
        reservations.save(result.reservation());
        String key = command.orderId().toString();
        if (!result.reserved()) {
            outbox.append(
                    AdjustStockUseCase.TOPIC,
                    key,
                    StockReservationFailed.TYPE,
                    new StockReservationFailed(command.orderId(), result.insufficientSkuIds()));
            return;
        }
        locked.values().forEach(stocks::save);
        outbox.append(
                AdjustStockUseCase.TOPIC,
                key,
                StockReserved.TYPE,
                new StockReserved(command.orderId()));
        result.reservation()
                .productIds()
                .forEach(
                        productId ->
                                AdjustStockUseCase.publishAvailability(
                                        outbox, availability.refresh(productId, now)));
    }
}
