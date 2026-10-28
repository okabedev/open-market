package com.openmarket.catalog.command.application;

import com.openmarket.catalog.command.domain.ProductAvailabilityCalculator;
import com.openmarket.catalog.command.domain.Stock;
import com.openmarket.catalog.command.domain.StockRepository;
import com.openmarket.catalog.command.domain.StockReservation;
import com.openmarket.catalog.command.domain.StockReservationRepository;
import com.openmarket.common.messaging.consumer.IgnoredEvents;
import com.openmarket.common.messaging.outbox.Outbox;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 취소된 주문({@code OrderCancelled})의 예약을 푼다. 예약이 없으면 빈 {@code RELEASED} 예약을 남겨, 뒤늦게 온 {@code
 * OrderPlaced}가 재고를 잡지 않게 한다(docs/architecture/catalog.md "재고 예약").
 */
@Service
public class ReleaseStockReservationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReleaseStockReservationUseCase.class);

    private final StockReservationRepository reservations;
    private final StockRepository stocks;
    private final ProductAvailabilityCalculator availability;
    private final Outbox outbox;
    private final Clock clock;

    public ReleaseStockReservationUseCase(
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
    public void execute(StockReservationCommand command) {
        Instant now = clock.instant();
        Optional<StockReservation> reservation =
                reservations.findForUpdateByOrderId(command.orderId());
        if (reservation.isEmpty()) {
            reservations.save(StockReservation.releasedWithoutReservation(command.orderId(), now));
            return;
        }
        StockReservation found = reservation.get();
        Map<UUID, Stock> locked = new HashMap<>();
        found.skuIds()
                .forEach(
                        skuId ->
                                stocks.findForUpdateBySkuId(skuId)
                                        .ifPresent(stock -> locked.put(skuId, stock)));
        switch (found.release(locked, now)) {
            case APPLIED -> {
                locked.values().forEach(stocks::save);
                reservations.save(found);
                found.productIds()
                        .forEach(
                                productId ->
                                        AdjustStockUseCase.publishAvailability(
                                                outbox, availability.refresh(productId, now)));
            }
            case ALREADY -> {}
            case INVALID ->
                    IgnoredEvents.warn(
                            log,
                            "예약을 해제할 수 없는 상태라 넘긴다: orderId={}, status={}",
                            command.orderId(),
                            found.status());
        }
    }
}
