package com.openmarket.catalog.command.application;

import static com.openmarket.catalog.command.application.CatalogFakes.WOMEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.catalog.command.domain.Product;
import com.openmarket.catalog.command.domain.ProductAvailabilityCalculator;
import com.openmarket.catalog.command.domain.ProductSnapshotFactory;
import com.openmarket.catalog.command.domain.Seller;
import com.openmarket.catalog.command.domain.Stock;
import com.openmarket.catalog.command.domain.StockAdjustment;
import com.openmarket.catalog.command.domain.StockAvailabilityChanged;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 클레임 재입고(docs/architecture/catalog.md "클레임 재입고"). */
class RestockClaimUseCaseTest {

    static final UUID SELLER = UUID.fromString("0199f5a0-0000-7000-8000-0000000000aa");
    static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");

    final CatalogFakes.Products products = new CatalogFakes.Products();
    final CatalogFakes.Stocks stocks = new CatalogFakes.Stocks();
    final CatalogFakes.Categories categories = new CatalogFakes.Categories();
    final CatalogFakes.Sellers sellers = new CatalogFakes.Sellers();
    final CatalogFakes.Availabilities availabilities = new CatalogFakes.Availabilities();
    final CatalogFakes.Adjustments adjustments = new CatalogFakes.Adjustments();
    final CatalogFakes.RecordingOutbox outbox = new CatalogFakes.RecordingOutbox();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final ProductAvailabilityCalculator availability =
            new ProductAvailabilityCalculator(stocks, availabilities);

    final RestockClaimUseCase restock =
            new RestockClaimUseCase(stocks, adjustments, availability, outbox, clock);

    final UUID claimId = UUID.randomUUID();
    UUID productId;
    UUID skuM;
    UUID skuL;

    @BeforeEach
    void givenSoldOutProduct() {
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
                                        new RegisterProductCommand.Sku(List.of("M"), 19_900, 0),
                                        new RegisterProductCommand.Sku(List.of("L"), 19_900, 0))));
        Product product = products.rows.get(productId);
        skuM = product.skus().get(0).id();
        skuL = product.skus().get(1).id();
        outbox.events.clear();
    }

    RestockClaimCommand command(RestockClaimCommand.Line... lines) {
        return new RestockClaimCommand(claimId, List.of(lines));
    }

    @Test
    @DisplayName("재입고 항목만큼 실재고를 늘리고 클레임 ID와 함께 이력을 남긴다. 품절이 풀리면 StockAvailabilityChanged를 낸다")
    void restocks() {
        restock.execute(
                command(
                        new RestockClaimCommand.Line(skuL, 1),
                        new RestockClaimCommand.Line(skuM, 2)));

        assertThat(stocks.rows.get(skuM).onHand()).isEqualTo(2);
        assertThat(stocks.rows.get(skuL).onHand()).isEqualTo(1);
        assertThat(adjustments.rows)
                .extracting(StockAdjustment::skuId, StockAdjustment::delta, StockAdjustment::reason)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(skuM, 2, "클레임 재입고"),
                        org.assertj.core.groups.Tuple.tuple(skuL, 1, "클레임 재입고"));
        assertThat(adjustments.rows).allMatch(row -> claimId.equals(row.claimId()));
        assertThat(outbox.events)
                .singleElement()
                .satisfies(
                        event -> {
                            assertThat(event.type()).isEqualTo(StockAvailabilityChanged.TYPE);
                            assertThat(((StockAvailabilityChanged) event.payload()).inStock())
                                    .isTrue();
                        });
    }

    @Test
    @DisplayName("이미 재입고한 클레임은 다시 더하지 않는다")
    void once() {
        restock.execute(command(new RestockClaimCommand.Line(skuM, 2)));
        restock.execute(command(new RestockClaimCommand.Line(skuM, 2)));

        assertThat(stocks.rows.get(skuM).onHand()).isEqualTo(2);
        assertThat(adjustments.rows).hasSize(1);
    }

    @Test
    @DisplayName("빈 항목이면 아무것도 하지 않고, 없는 SKU는 넘긴다. 상한을 넘으면 상한까지만 더한다")
    void edges() {
        restock.execute(command());
        assertThat(adjustments.rows).isEmpty();

        stocks.rows.get(skuM).adjust(Stock.MAX_ON_HAND - 1, NOW);
        restock.execute(
                command(
                        new RestockClaimCommand.Line(UUID.randomUUID(), 1),
                        new RestockClaimCommand.Line(skuM, 5)));

        assertThat(stocks.rows.get(skuM).onHand()).isEqualTo(Stock.MAX_ON_HAND);
        assertThat(adjustments.rows)
                .singleElement()
                .satisfies(row -> assertThat(row.delta()).isEqualTo(1));
    }
}
