package com.openmarket.catalog.command.application;

import static com.openmarket.catalog.command.application.CatalogFakes.WOMEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.catalog.command.domain.Product;
import com.openmarket.catalog.command.domain.ProductAvailabilityCalculator;
import com.openmarket.catalog.command.domain.ProductSnapshotFactory;
import com.openmarket.catalog.command.domain.Seller;
import com.openmarket.catalog.command.domain.StockAvailabilityChanged;
import com.openmarket.catalog.command.domain.StockReservationFailed;
import com.openmarket.catalog.command.domain.StockReservationStatus;
import com.openmarket.catalog.command.domain.StockReserved;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 재고 예약·확정·해제(CAT-4). 동시성은 {@code StockReservationIntegrationTest}가 실제 DB로 확인한다. */
class ReservationUseCasesTest {

    static final UUID SELLER = UUID.fromString("0199f5a0-0000-7000-8000-0000000000aa");
    static final Instant NOW = Instant.parse("2026-10-20T08:00:00Z");

    final CatalogFakes.Products products = new CatalogFakes.Products();
    final CatalogFakes.Stocks stocks = new CatalogFakes.Stocks();
    final CatalogFakes.Categories categories = new CatalogFakes.Categories();
    final CatalogFakes.Sellers sellers = new CatalogFakes.Sellers();
    final CatalogFakes.Availabilities availabilities = new CatalogFakes.Availabilities();
    final CatalogFakes.Reservations reservations = new CatalogFakes.Reservations();
    final CatalogFakes.RecordingOutbox outbox = new CatalogFakes.RecordingOutbox();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final ProductAvailabilityCalculator availability =
            new ProductAvailabilityCalculator(stocks, availabilities);

    final ReserveStockUseCase reserve =
            new ReserveStockUseCase(reservations, stocks, availability, outbox, clock);
    final CommitStockReservationUseCase commit =
            new CommitStockReservationUseCase(reservations, stocks, availability, outbox, clock);
    final ReleaseStockReservationUseCase release =
            new ReleaseStockReservationUseCase(reservations, stocks, availability, outbox, clock);

    final UUID orderId = UUID.randomUUID();
    UUID productId;
    UUID skuM;
    UUID skuL;

    @BeforeEach
    void givenProduct() {
        sellers.save(Seller.approve(SELLER, "길동상회", NOW));
        RegisterProductUseCase register =
                new RegisterProductUseCase(
                        products,
                        stocks,
                        categories,
                        sellers,
                        availability,
                        new ProductSnapshotFactory(categories, sellers, stocks, availabilities),
                        outbox,
                        clock);
        productId =
                register.execute(
                        new RegisterProductCommand(
                                SELLER,
                                WOMEN,
                                "반팔 티셔츠",
                                "면 100%",
                                List.of(UUID.randomUUID()),
                                List.of("사이즈"),
                                List.of(
                                        new RegisterProductCommand.Sku(List.of("M"), 19_900, 2),
                                        new RegisterProductCommand.Sku(List.of("L"), 19_900, 3))));
        Product product = products.rows.get(productId);
        skuM = product.skus().get(0).id();
        skuL = product.skus().get(1).id();
        outbox.events.clear();
    }

    @Test
    @DisplayName("모두 충분하면 예약하고 StockReserved를 orderId 키로 낸다. 같은 SKU 줄은 수량을 합친다")
    void reserves() {
        reserve.execute(command(line(skuM, 1), line(skuM, 1), line(skuL, 1)));

        assertThat(stocks.rows.get(skuM).reserved()).isEqualTo(2);
        assertThat(stocks.rows.get(skuL).reserved()).isEqualTo(1);
        assertThat(reservations.rows.get(orderId).status())
                .isEqualTo(StockReservationStatus.RESERVED);
        assertThat(outbox.events)
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.aggregateType()).isEqualTo("catalog.stock");
                            assertThat(e.aggregateId()).isEqualTo(orderId.toString());
                            assertThat(e.payload()).isEqualTo(new StockReserved(orderId));
                        });
    }

    @Test
    @DisplayName("예약으로 상품의 판매 가능한 SKU가 없어지면 StockAvailabilityChanged도 낸다. 풀면 다시 낸다")
    void availabilityFollowsReservation() {
        reserve.execute(command(line(skuM, 2), line(skuL, 3)));

        assertThat(outbox.types()).containsExactly("StockReserved", "StockAvailabilityChanged");
        assertThat(((StockAvailabilityChanged) outbox.events.getLast().payload()).inStock())
                .isFalse();

        outbox.events.clear();
        release.execute(new StockReservationCommand(orderId));

        assertThat(stocks.rows.get(skuM).reserved()).isZero();
        assertThat(outbox.types()).containsExactly("StockAvailabilityChanged");
        assertThat(((StockAvailabilityChanged) outbox.events.getLast().payload()).inStock())
                .isTrue();
    }

    @Test
    @DisplayName("하나라도 모자라면 아무것도 예약하지 않고 StockReservationFailed에 모자란 SKU를 담는다")
    void failsWhenInsufficient() {
        UUID unknown = UUID.randomUUID();

        reserve.execute(command(line(skuM, 3), line(skuL, 1), line(unknown, 1)));

        assertThat(stocks.rows.get(skuM).reserved()).isZero();
        assertThat(stocks.rows.get(skuL).reserved()).isZero();
        assertThat(reservations.rows.get(orderId).status())
                .isEqualTo(StockReservationStatus.FAILED);
        assertThat(outbox.events)
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.type()).isEqualTo("StockReservationFailed");
                            assertThat(((StockReservationFailed) e.payload()).insufficientSkuIds())
                                    .containsExactlyInAnyOrder(skuM, unknown);
                        });

        outbox.events.clear();
        release.execute(new StockReservationCommand(orderId));
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("같은 주문의 OrderPlaced가 다시 오면 다시 예약하지 않는다")
    void reserveOncePerOrder() {
        reserve.execute(command(line(skuM, 1)));
        reserve.execute(command(line(skuM, 1)));

        assertThat(stocks.rows.get(skuM).reserved()).isEqualTo(1);
        assertThat(outbox.types()).containsExactly("StockReserved");
    }

    @Test
    @DisplayName("확정하면 실재고에서 빼고 판매 가능 여부는 그대로다. 다시 확정해도 그대로다")
    void commits() {
        reserve.execute(command(line(skuM, 2)));
        outbox.events.clear();

        commit.execute(new StockReservationCommand(orderId));
        commit.execute(new StockReservationCommand(orderId));

        assertThat(stocks.rows.get(skuM).onHand()).isZero();
        assertThat(stocks.rows.get(skuM).reserved()).isZero();
        assertThat(reservations.rows.get(orderId).status())
                .isEqualTo(StockReservationStatus.COMMITTED);
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("예약 없이 취소가 먼저 오면 빈 예약을 남겨, 뒤늦은 OrderPlaced가 재고를 잡지 않는다")
    void cancelBeforePlaced() {
        release.execute(new StockReservationCommand(orderId));
        reserve.execute(command(line(skuM, 1)));

        assertThat(stocks.rows.get(skuM).reserved()).isZero();
        assertThat(reservations.rows.get(orderId).status())
                .isEqualTo(StockReservationStatus.RELEASED);
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("예약이 없거나 풀린 주문의 확정은 무시한다")
    void commitWithoutReservationIsIgnored() {
        commit.execute(new StockReservationCommand(orderId));
        assertThat(reservations.rows).isEmpty();

        reserve.execute(command(line(skuM, 1)));
        release.execute(new StockReservationCommand(orderId));
        commit.execute(new StockReservationCommand(orderId));
        assertThat(stocks.rows.get(skuM).onHand()).isEqualTo(2);
        assertThat(reservations.rows.get(orderId).status())
                .isEqualTo(StockReservationStatus.RELEASED);
    }

    private ReserveStockCommand command(ReserveStockCommand.Line... lines) {
        return new ReserveStockCommand(orderId, List.of(lines));
    }

    private static ReserveStockCommand.Line line(UUID skuId, int quantity) {
        return new ReserveStockCommand.Line(skuId, quantity);
    }
}
