package com.openmarket.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.catalog.command.application.AdjustStockCommand;
import com.openmarket.catalog.command.application.AdjustStockUseCase;
import com.openmarket.catalog.command.application.RegisterProductCommand;
import com.openmarket.catalog.command.application.RegisterProductUseCase;
import com.openmarket.catalog.command.application.RegisterSellerCommand;
import com.openmarket.catalog.command.application.RegisterSellerUseCase;
import com.openmarket.catalog.command.application.ReserveStockCommand;
import com.openmarket.catalog.command.application.ReserveStockUseCase;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 재고는 초과 판매되지 않는다(CAT-4). 동시에 들어온 주문을 실제 DB 잠금으로 확인한다. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(PostgresContainerConfiguration.class)
class StockReservationConcurrencyIntegrationTest {

    static final int THREADS = 16;

    @Autowired RegisterSellerUseCase registerSeller;

    @Autowired RegisterProductUseCase registerProduct;

    @Autowired ReserveStockUseCase reserve;

    @Autowired AdjustStockUseCase adjust;

    @Autowired JdbcClient jdbc;

    UUID sellerId;
    UUID productId;
    UUID skuA;
    UUID skuB;

    @BeforeEach
    void givenProduct() {
        sellerId = UUID.randomUUID();
        registerSeller.execute(new RegisterSellerCommand(sellerId, "길동상회"));
        productId =
                registerProduct.execute(
                        new RegisterProductCommand(
                                sellerId,
                                UUID.fromString("0199f000-0000-7000-8000-000000000101"),
                                "반팔 티셔츠",
                                "면 100%",
                                List.of(UUID.randomUUID()),
                                List.of("사이즈"),
                                List.of(
                                        new RegisterProductCommand.Sku(List.of("M"), 19_900, 5),
                                        new RegisterProductCommand.Sku(List.of("L"), 19_900, 5))));
        List<UUID> skus =
                jdbc.sql("SELECT id FROM sku WHERE product_id = :id ORDER BY position")
                        .param("id", productId)
                        .query(UUID.class)
                        .list();
        skuA = skus.get(0);
        skuB = skus.get(1);
    }

    @Test
    @DisplayName("재고 5개에 주문 40건이 동시에 오면 정확히 5건만 예약되고 나머지는 실패한다")
    void neverOversells() throws Exception {
        List<UUID> orders = new ArrayList<>();
        List<Callable<Void>> calls = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            UUID orderId = UUID.randomUUID();
            orders.add(orderId);
            calls.add(
                    () -> {
                        reserve.execute(
                                new ReserveStockCommand(
                                        orderId, List.of(new ReserveStockCommand.Line(skuA, 1))));
                        return null;
                    });
        }

        runConcurrently(calls);

        assertThat(statusCounts(orders)).isEqualTo(Map.of("RESERVED", 5L, "FAILED", 35L));
        assertThat(reserved(skuA)).isEqualTo(5);
        assertThat(onHand(skuA)).isEqualTo(5);
    }

    @Test
    @DisplayName("SKU 두 개를 서로 반대 순서로 담은 주문이 동시에 와도 교착 없이 모두 처리되고 수량이 맞는다")
    void noDeadlockOnOverlappingSkus() throws Exception {
        List<UUID> orders = new ArrayList<>();
        List<Callable<Void>> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UUID orderId = UUID.randomUUID();
            orders.add(orderId);
            List<ReserveStockCommand.Line> lines =
                    i % 2 == 0
                            ? List.of(
                                    new ReserveStockCommand.Line(skuA, 1),
                                    new ReserveStockCommand.Line(skuB, 1))
                            : List.of(
                                    new ReserveStockCommand.Line(skuB, 1),
                                    new ReserveStockCommand.Line(skuA, 1));
            calls.add(
                    () -> {
                        reserve.execute(new ReserveStockCommand(orderId, lines));
                        return null;
                    });
        }

        runConcurrently(calls);

        assertThat(statusCounts(orders)).isEqualTo(Map.of("RESERVED", 5L, "FAILED", 15L));
        assertThat(reserved(skuA)).isEqualTo(5);
        assertThat(reserved(skuB)).isEqualTo(5);
    }

    @Test
    @DisplayName("예약과 재고 조정이 동시에 와도 실재고가 예약 수량보다 작아지지 않는다")
    void adjustmentsRespectReservations() throws Exception {
        List<UUID> orders = new ArrayList<>();
        List<Callable<Void>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID orderId = UUID.randomUUID();
            orders.add(orderId);
            calls.add(
                    () -> {
                        reserve.execute(
                                new ReserveStockCommand(
                                        orderId, List.of(new ReserveStockCommand.Line(skuA, 1))));
                        return null;
                    });
            calls.add(
                    () -> {
                        try {
                            adjust.execute(
                                    new AdjustStockCommand(sellerId, productId, skuA, -1, null));
                        } catch (RuntimeException e) {
                            // 예약 때문에 줄일 수 없으면 422(CATALOG_STOCK_OUT_OF_RANGE)다.
                        }
                        return null;
                    });
        }

        runConcurrently(calls);

        long reservedOrders = statusCounts(orders).getOrDefault("RESERVED", 0L);
        assertThat(reserved(skuA)).isEqualTo((int) reservedOrders);
        assertThat(onHand(skuA)).isGreaterThanOrEqualTo(reserved(skuA));
    }

    private static void runConcurrently(List<Callable<Void>> calls) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (Future<Void> result : pool.invokeAll(calls)) {
                result.get();
            }
        }
    }

    private Map<String, Long> statusCounts(List<UUID> orders) {
        Map<String, Long> counts = new java.util.HashMap<>();
        jdbc.sql(
                        "SELECT status, count(*) AS n FROM stock_reservation WHERE order_id IN (:ids)"
                                + " GROUP BY status")
                .param("ids", orders)
                .query((rs, row) -> counts.put(rs.getString("status"), rs.getLong("n")))
                .list();
        return counts;
    }

    private int reserved(UUID skuId) {
        return jdbc.sql("SELECT reserved FROM stock WHERE sku_id = :id")
                .param("id", skuId)
                .query(Integer.class)
                .single();
    }

    private int onHand(UUID skuId) {
        return jdbc.sql("SELECT on_hand FROM stock WHERE sku_id = :id")
                .param("id", skuId)
                .query(Integer.class)
                .single();
    }
}
