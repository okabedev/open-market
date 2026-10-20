package com.openmarket.catalog.command.application;

import com.openmarket.catalog.command.domain.ProductAvailabilityCalculator;
import com.openmarket.catalog.command.domain.Stock;
import com.openmarket.catalog.command.domain.StockRepository;
import com.openmarket.catalog.command.domain.StockReservation;
import com.openmarket.catalog.command.domain.StockReservationRepository;
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
 * 결제가 끝난 주문({@code OrderPaid})의 예약을 확정한다. 실재고와 예약 수량을 함께 줄인다. 예약이 없거나 풀린 주문이면 경고 로그만 남긴다
 * (docs/architecture/catalog.md "재고 예약").
 */
@Service
public class CommitStockReservationUseCase {

    private static final Logger log = LoggerFactory.getLogger(CommitStockReservationUseCase.class);

    private final StockReservationRepository reservations;
    private final StockRepository stocks;
    private final ProductAvailabilityCalculator availability;
    private final Outbox outbox;
    private final Clock clock;

    public CommitStockReservationUseCase(
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
            log.warn("예약이 없는 주문의 확정이라 넘긴다: orderId={}", command.orderId());
            return;
        }
        StockReservation found = reservation.get();
        Map<UUID, Stock> locked = new HashMap<>();
        found.skuIds()
                .forEach(
                        skuId ->
                                stocks.findForUpdateBySkuId(skuId)
                                        .ifPresent(stock -> locked.put(skuId, stock)));
        switch (found.commit(locked, now)) {
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
                    log.warn(
                            "예약을 확정할 수 없는 상태라 넘긴다: orderId={}, status={}",
                            command.orderId(),
                            found.status());
        }
    }
}
