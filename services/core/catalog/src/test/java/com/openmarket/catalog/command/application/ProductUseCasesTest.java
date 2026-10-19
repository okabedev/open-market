package com.openmarket.catalog.command.application;

import static com.openmarket.catalog.command.application.CatalogFakes.FASHION;
import static com.openmarket.catalog.command.application.CatalogFakes.WOMEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.catalog.command.domain.Product;
import com.openmarket.catalog.command.domain.ProductAvailabilityCalculator;
import com.openmarket.catalog.command.domain.ProductSnapshot;
import com.openmarket.catalog.command.domain.ProductSnapshotFactory;
import com.openmarket.catalog.command.domain.ProductStatus;
import com.openmarket.catalog.command.domain.Seller;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductUseCasesTest {

    static final UUID SELLER = UUID.fromString("0199f5a0-0000-7000-8000-0000000000aa");
    static final UUID IMAGE = UUID.fromString("0199f5a0-0000-7000-8000-0000000000f1");
    static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

    final CatalogFakes.Products products = new CatalogFakes.Products();
    final CatalogFakes.Stocks stocks = new CatalogFakes.Stocks();
    final CatalogFakes.Categories categories = new CatalogFakes.Categories();
    final CatalogFakes.Sellers sellers = new CatalogFakes.Sellers();
    final CatalogFakes.RecordingOutbox outbox = new CatalogFakes.RecordingOutbox();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final CatalogFakes.Availabilities availabilities = new CatalogFakes.Availabilities();
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
    final PublishProductUseCase publish =
            new PublishProductUseCase(products, sellers, snapshots, outbox, clock);

    @BeforeEach
    void givenSeller() {
        sellers.save(Seller.approve(SELLER, "길동상회", NOW));
    }

    @Test
    @DisplayName("등록하면 DRAFT 상품과 SKU별 재고를 만들고 ProductRegistered 스냅샷을 낸다")
    void registersProduct() {
        UUID productId = register.execute(registerCommand(List.of(IMAGE), 5, 0));

        Product product = products.rows.get(productId);
        assertThat(product.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(stocks.findByProductId(productId))
                .extracting(s -> s.onHand())
                .containsExactlyInAnyOrder(5, 0);
        assertThat(outbox.types()).containsExactly("ProductRegistered");
        assertThat(outbox.events.getFirst().aggregateType()).isEqualTo("catalog.product");
        assertThat(outbox.events.getFirst().aggregateId()).isEqualTo(productId.toString());

        ProductSnapshot snapshot = outbox.lastSnapshot();
        assertThat(snapshot.revision()).isEqualTo(1);
        assertThat(snapshot.status()).isEqualTo("DRAFT");
        assertThat(snapshot.storeName()).isEqualTo("길동상회");
        assertThat(snapshot.categoryPath()).containsExactly(FASHION, WOMEN);
        assertThat(snapshot.imageFileIds()).containsExactly(IMAGE);
        assertThat(snapshot.minPrice()).isEqualTo(19_900);
        assertThat(snapshot.skus())
                .extracting(ProductSnapshot.SkuSnapshot::inStock)
                .containsExactly(true, false);
        assertThat(snapshot.inStock()).isTrue();
        assertThat(snapshot.availabilityRevision()).isEqualTo(1);
        assertThat(snapshot.publishedAt()).isNull();
    }

    @Test
    @DisplayName("판매자 사본이 아직 없으면 409 CATALOG_SELLER_NOT_READY다")
    void requiresSellerCopy() {
        sellers.rows.clear();

        assertThatThrownBy(() -> register.execute(registerCommand(List.of(), 1, 1)))
                .hasFieldOrPropertyWithValue("code", "CATALOG_SELLER_NOT_READY");
    }

    @Test
    @DisplayName("없는 카테고리나 대분류에는 등록할 수 없다")
    void requiresLeafCategory() {
        assertThatThrownBy(() -> register.execute(registerCommand(FASHION)))
                .hasFieldOrPropertyWithValue("code", "CATALOG_INVALID_INPUT");
        assertThatThrownBy(() -> register.execute(registerCommand(UUID.randomUUID())))
                .hasFieldOrPropertyWithValue("code", "CATALOG_INVALID_INPUT");
    }

    @Test
    @DisplayName("판매를 시작하면 ProductPublished를 내고, 다시 해도 이벤트를 내지 않는다")
    void publishesOnce() {
        UUID productId = register.execute(registerCommand(List.of(IMAGE), 1, 1));

        publish.execute(new PublishProductCommand(SELLER, productId));
        publish.execute(new PublishProductCommand(SELLER, productId));

        assertThat(outbox.types()).containsExactly("ProductRegistered", "ProductPublished");
        ProductSnapshot snapshot = outbox.lastSnapshot();
        assertThat(snapshot.status()).isEqualTo("ON_SALE");
        assertThat(snapshot.revision()).isEqualTo(2);
        assertThat(snapshot.publishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("다른 판매자의 상품은 404 CATALOG_PRODUCT_NOT_FOUND다")
    void hidesOtherSellersProduct() {
        UUID productId = register.execute(registerCommand(List.of(IMAGE), 1, 1));
        UUID other = UUID.randomUUID();

        assertThatThrownBy(() -> publish.execute(new PublishProductCommand(other, productId)))
                .hasFieldOrPropertyWithValue("code", "CATALOG_PRODUCT_NOT_FOUND");
        assertThatThrownBy(() -> update.execute(updateCommand(other, productId, List.of())))
                .hasFieldOrPropertyWithValue("code", "CATALOG_PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("수정하면 새 SKU의 재고를 만들고 ProductChanged 스냅샷을 낸다")
    void updatesProduct() {
        UUID productId = register.execute(registerCommand(List.of(IMAGE), 1, 1));
        Product product = products.rows.get(productId);
        UUID m = product.skus().get(0).id();
        UUID l = product.skus().get(1).id();

        update.execute(
                updateCommand(
                        SELLER,
                        productId,
                        List.of(
                                new UpdateProductCommand.Sku(m, List.of("블랙", "M"), 18_900, 0),
                                new UpdateProductCommand.Sku(l, List.of("블랙", "L"), 21_900, 0),
                                new UpdateProductCommand.Sku(
                                        null, List.of("화이트", "M"), 19_900, 7))));

        assertThat(stocks.findByProductId(productId)).hasSize(3);
        assertThat(outbox.types()).containsExactly("ProductRegistered", "ProductChanged");
        ProductSnapshot snapshot = outbox.lastSnapshot();
        assertThat(snapshot.name()).isEqualTo("새 이름");
        assertThat(snapshot.minPrice()).isEqualTo(18_900);
        assertThat(snapshot.skus()).hasSize(3);
        assertThat(snapshot.revision()).isEqualTo(2);
    }

    RegisterProductCommand registerCommand(List<UUID> images, int stockM, int stockL) {
        return new RegisterProductCommand(
                SELLER,
                WOMEN,
                "반팔 티셔츠",
                "면 100%",
                images,
                List.of("색상", "사이즈"),
                List.of(
                        new RegisterProductCommand.Sku(List.of("블랙", "M"), 19_900, stockM),
                        new RegisterProductCommand.Sku(List.of("블랙", "L"), 21_900, stockL)));
    }

    RegisterProductCommand registerCommand(UUID categoryId) {
        return new RegisterProductCommand(
                SELLER,
                categoryId,
                "반팔 티셔츠",
                "면 100%",
                List.of(),
                List.of(),
                List.of(new RegisterProductCommand.Sku(List.of(), 19_900, 1)));
    }

    UpdateProductCommand updateCommand(
            UUID seller, UUID productId, List<UpdateProductCommand.Sku> skus) {
        return new UpdateProductCommand(
                seller, productId, WOMEN, "새 이름", "새 설명", List.of(IMAGE), skus);
    }
}
