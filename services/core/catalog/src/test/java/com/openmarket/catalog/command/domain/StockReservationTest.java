package com.openmarket.catalog.command.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.catalog.command.domain.StockReservation.Transition;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StockReservationTest {

    static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");

    final UUID orderId = UUID.randomUUID();
    final UUID productId = UUID.randomUUID();
    final UUID skuA = UUID.randomUUID();
    final UUID skuB = UUID.randomUUID();
    final Map<UUID, Stock> stocks = new HashMap<>();

    StockReservationTest() {
        stocks.put(skuA, Stock.create(skuA, productId, 5, NOW));
        stocks.put(skuB, Stock.create(skuB, productId, 1, NOW));
    }

    @Test
    @DisplayName("모든 SKU가 충분하면 예약 수량을 올리고 RESERVED가 된다")
    void reservesAll() {
        StockReservation.Result result =
                StockReservation.reserve(orderId, Map.of(skuA, 3, skuB, 1), stocks, NOW);

        assertThat(result.reservation().status()).isEqualTo(StockReservationStatus.RESERVED);
        assertThat(result.insufficientSkuIds()).isEmpty();
        assertThat(stocks.get(skuA).reserved()).isEqualTo(3);
        assertThat(stocks.get(skuB).available()).isZero();
        assertThat(result.reservation().productIds()).containsExactly(productId);
    }

    @Test
    @DisplayName("하나라도 모자라거나 없으면 아무것도 예약하지 않고 FAILED다. 모자란 SKU를 알려 준다")
    void failsWithoutPartialReservation() {
        UUID missing = UUID.randomUUID();

        StockReservation.Result result =
                StockReservation.reserve(
                        orderId, Map.of(skuA, 3, skuB, 2, missing, 1), stocks, NOW);

        assertThat(result.reservation().status()).isEqualTo(StockReservationStatus.FAILED);
        assertThat(result.insufficientSkuIds()).containsExactlyInAnyOrder(skuB, missing);
        assertThat(stocks.get(skuA).reserved()).isZero();
        assertThat(stocks.get(skuB).reserved()).isZero();
    }

    @Test
    @DisplayName("확정하면 실재고와 예약 수량을 함께 줄인다. 다시 확정하면 ALREADY다")
    void commit() {
        StockReservation reservation =
                StockReservation.reserve(orderId, Map.of(skuA, 3), stocks, NOW).reservation();

        assertThat(reservation.commit(stocks, NOW)).isEqualTo(Transition.APPLIED);
        assertThat(stocks.get(skuA).onHand()).isEqualTo(2);
        assertThat(stocks.get(skuA).reserved()).isZero();
        assertThat(reservation.status()).isEqualTo(StockReservationStatus.COMMITTED);
        assertThat(reservation.commit(stocks, NOW)).isEqualTo(Transition.ALREADY);
        assertThat(stocks.get(skuA).onHand()).isEqualTo(2);
        assertThat(reservation.release(stocks, NOW)).isEqualTo(Transition.INVALID);
    }

    @Test
    @DisplayName("풀면 예약 수량만 줄인다. 푼 예약은 확정할 수 없다")
    void release() {
        StockReservation reservation =
                StockReservation.reserve(orderId, Map.of(skuA, 3), stocks, NOW).reservation();

        assertThat(reservation.release(stocks, NOW)).isEqualTo(Transition.APPLIED);
        assertThat(stocks.get(skuA).onHand()).isEqualTo(5);
        assertThat(stocks.get(skuA).reserved()).isZero();
        assertThat(reservation.release(stocks, NOW)).isEqualTo(Transition.ALREADY);
        assertThat(reservation.commit(stocks, NOW)).isEqualTo(Transition.INVALID);
    }

    @Test
    @DisplayName("실패한 예약은 풀어도 재고가 그대로고, 확정은 INVALID다")
    void failedReservation() {
        StockReservation reservation =
                StockReservation.reserve(orderId, Map.of(skuB, 2), stocks, NOW).reservation();

        assertThat(reservation.release(stocks, NOW)).isEqualTo(Transition.ALREADY);
        assertThat(reservation.commit(stocks, NOW)).isEqualTo(Transition.INVALID);
        assertThat(stocks.get(skuB).reserved()).isZero();
    }

    @Test
    @DisplayName("예약 없이 취소된 주문은 빈 RELEASED 예약으로 남는다")
    void releasedWithoutReservation() {
        StockReservation reservation = StockReservation.releasedWithoutReservation(orderId, NOW);

        assertThat(reservation.status()).isEqualTo(StockReservationStatus.RELEASED);
        assertThat(reservation.skuIds()).isEmpty();
    }
}
