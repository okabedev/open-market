package com.openmarket.catalog.command.application;

import static com.openmarket.catalog.command.application.CatalogFakes.WOMEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.catalog.command.domain.Product;
import com.openmarket.catalog.command.domain.ProductAvailabilityCalculator;
import com.openmarket.catalog.command.domain.ProductSnapshot;
import com.openmarket.catalog.command.domain.ProductSnapshotFactory;
import com.openmarket.catalog.command.domain.Seller;
import com.openmarket.catalog.command.domain.StockAvailabilityChanged;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 재고 조정(CAT-3)과 품절 전환 이벤트. */
class StockUseCasesTest {

    static final UUID SELLER = UUID.fromString("0199f5a0-0000-7000-8000-0000000000aa");
    static final UUID IMAGE = UUID.fromString("0199f5a0-0000-7000-8000-0000000000f1");
    static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

    final CatalogFakes.Products products = new CatalogFakes.Products();
    final CatalogFakes.Stocks stocks = new CatalogFakes.Stocks();
    final CatalogFakes.Categories categories = new CatalogFakes.Categories();
    final CatalogFakes.Sellers sellers = new CatalogFakes.Sellers();
    final CatalogFakes.Availabilities availabilities = new CatalogFakes.Availabilities();
    final CatalogFakes.Adjustments adjustments = new CatalogFakes.Adjustments();
    final CatalogFakes.RecordingOutbox outbox = new CatalogFakes.RecordingOutbox();
    final CatalogFakes.RecordingAuditLog auditLog = new CatalogFakes.RecordingAuditLog();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final ProductSnapshotFactory snapshots =
            new ProductSnapshotFactory(categories, sellers, stocks, availabilities);
    final ProductAvailabilityCalculator availability =
            new ProductAvailabilityCalculator(stocks, availabilities);

    final RegisterProductUseCase register =
            new RegisterProductUseCase(
                    products, stocks, categories, sellers, availability, snapshots, outbox, clock);
    final UpdateProductUseCase update =
            new UpdateProductUseCase(
                    products, stocks, categories, availability, snapshots, outbox, clock);
    final BanProductUseCase ban = new BanProductUseCase(products, outbox, auditLog, clock);
    final AdjustStockUseCase adjust =
            new AdjustStockUseCase(products, stocks, adjustments, availability, outbox, clock);

    UUID productId;
    UUID skuM;
    UUID skuL;

    @BeforeEach
    void givenProduct() {
        sellers.save(Seller.approve(SELLER, "길동상회", NOW));
        productId =
                register.execute(
                        new RegisterProductCommand(
                                SELLER,
                                WOMEN,
                                "반팔 티셔츠",
                                "면 100%",
                                List.of(IMAGE),
                                List.of("사이즈"),
                                List.of(
                                        new RegisterProductCommand.Sku(List.of("M"), 19_900, 2),
                                        new RegisterProductCommand.Sku(List.of("L"), 19_900, 0))));
        Product product = products.rows.get(productId);
        skuM = product.skus().get(0).id();
        skuL = product.skus().get(1).id();
        outbox.events.clear();
    }

    @Test
    @DisplayName("등록하면 판매 가능 여부를 리비전 1로 만들고 스냅샷에 넣는다")
    void registerCreatesAvailability() {
        assertThat(availabilities.rows.get(productId).inStock()).isTrue();
        assertThat(availabilities.rows.get(productId).revision()).isEqualTo(1);
    }

    @Test
    @DisplayName("조정하면 실재고를 바꾸고 이력을 남긴다. 판매 가능 여부가 그대로면 이벤트를 내지 않는다")
    void adjustsStock() {
        AdjustStockUseCase.Result result = adjust.execute(command(skuL, 5, "  입고 "));

        assertThat(result.onHand()).isEqualTo(5);
        assertThat(result.reserved()).isZero();
        assertThat(result.available()).isEqualTo(5);
        assertThat(stocks.rows.get(skuL).onHand()).isEqualTo(5);
        assertThat(adjustments.rows)
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.id()).isEqualTo(result.adjustmentId());
                            assertThat(a.skuId()).isEqualTo(skuL);
                            assertThat(a.delta()).isEqualTo(5);
                            assertThat(a.onHandAfter()).isEqualTo(5);
                            assertThat(a.reason()).isEqualTo("입고");
                        });
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("모든 SKU가 품절되거나 다시 들어오면 리비전을 올린 StockAvailabilityChanged를 catalog.stock에 낸다")
    void publishesAvailabilityChanges() {
        adjust.execute(command(skuM, -2, null));
        adjust.execute(command(skuL, 3, null));

        assertThat(outbox.types())
                .containsExactly("StockAvailabilityChanged", "StockAvailabilityChanged");
        assertThat(outbox.events)
                .allSatisfy(
                        e -> {
                            assertThat(e.aggregateType()).isEqualTo("catalog.stock");
                            assertThat(e.aggregateId()).isEqualTo(productId.toString());
                        });
        assertThat(outbox.events)
                .extracting(e -> (StockAvailabilityChanged) e.payload())
                .containsExactly(
                        new StockAvailabilityChanged(productId, false, 2),
                        new StockAvailabilityChanged(productId, true, 3));
    }

    @Test
    @DisplayName("품절 상품에 재고 있는 SKU를 더하면 품절 전환 이벤트를 내고, 스냅샷에 같은 리비전이 들어간다")
    void newSkuRestocks() {
        adjust.execute(command(skuM, -2, null));
        outbox.events.clear();

        update.execute(
                new UpdateProductCommand(
                        SELLER,
                        productId,
                        WOMEN,
                        "반팔 티셔츠",
                        "면 100%",
                        List.of(IMAGE),
                        List.of(
                                new UpdateProductCommand.Sku(skuM, List.of("M"), 19_900, 0),
                                new UpdateProductCommand.Sku(skuL, List.of("L"), 19_900, 0),
                                new UpdateProductCommand.Sku(null, List.of("XL"), 19_900, 4))));

        assertThat(outbox.types()).containsExactly("StockAvailabilityChanged", "ProductChanged");
        ProductSnapshot snapshot = outbox.lastSnapshot();
        assertThat(snapshot.inStock()).isTrue();
        assertThat(snapshot.availabilityRevision()).isEqualTo(3);
    }

    @Test
    @DisplayName("조정 뒤 실재고가 0보다 작거나 999,999보다 크면 422 CATALOG_STOCK_OUT_OF_RANGE이고 아무것도 바꾸지 않는다")
    void rejectsOutOfRange() {
        assertThatThrownBy(() -> adjust.execute(command(skuM, -3, null)))
                .hasFieldOrPropertyWithValue("code", "CATALOG_STOCK_OUT_OF_RANGE");
        assertThatThrownBy(() -> adjust.execute(command(skuM, 999_998, null)))
                .hasFieldOrPropertyWithValue("code", "CATALOG_STOCK_OUT_OF_RANGE");

        assertThat(stocks.rows.get(skuM).onHand()).isEqualTo(2);
        assertThat(adjustments.rows).isEmpty();
    }

    @Test
    @DisplayName("증감 수량이 0이거나 범위를 벗어나거나 사유가 100자를 넘으면 400 CATALOG_INVALID_INPUT이다")
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> adjust.execute(command(skuM, 0, null)))
                .hasFieldOrPropertyWithValue("code", "CATALOG_INVALID_INPUT");
        assertThatThrownBy(() -> adjust.execute(command(skuM, 1_000_000, null)))
                .hasFieldOrPropertyWithValue("code", "CATALOG_INVALID_INPUT");
        assertThatThrownBy(() -> adjust.execute(command(skuM, 1, "가".repeat(101))))
                .hasFieldOrPropertyWithValue("code", "CATALOG_INVALID_INPUT");
    }

    @Test
    @DisplayName("다른 판매자의 상품은 404 상품, 상품에 없는 SKU는 404 SKU, 제재된 상품은 422다")
    void rejectsOthers() {
        assertThatThrownBy(
                        () ->
                                adjust.execute(
                                        new AdjustStockCommand(
                                                UUID.randomUUID(), productId, skuM, 1, null)))
                .hasFieldOrPropertyWithValue("code", "CATALOG_PRODUCT_NOT_FOUND");
        assertThatThrownBy(() -> adjust.execute(command(UUID.randomUUID(), 1, null)))
                .hasFieldOrPropertyWithValue("code", "CATALOG_SKU_NOT_FOUND");

        ban.execute(new BanProductCommand(UUID.randomUUID(), productId, "허위 광고"));
        assertThatThrownBy(() -> adjust.execute(command(skuM, 1, null)))
                .hasFieldOrPropertyWithValue("code", "CATALOG_PRODUCT_BANNED");
    }

    AdjustStockCommand command(UUID skuId, int delta, @Nullable String reason) {
        return new AdjustStockCommand(SELLER, productId, skuId, delta, reason);
    }
}
