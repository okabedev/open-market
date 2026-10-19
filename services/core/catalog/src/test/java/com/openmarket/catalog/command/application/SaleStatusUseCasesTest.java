package com.openmarket.catalog.command.application;

import static com.openmarket.catalog.command.application.CatalogFakes.WOMEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.catalog.command.domain.ProductBanned;
import com.openmarket.catalog.command.domain.ProductSnapshotFactory;
import com.openmarket.catalog.command.domain.ProductStatus;
import com.openmarket.catalog.command.domain.ProductStopped;
import com.openmarket.catalog.command.domain.Seller;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SaleStatusUseCasesTest {

    static final UUID SELLER = UUID.fromString("0199f5a0-0000-7000-8000-0000000000aa");
    static final UUID ADMIN = UUID.fromString("0199f5a0-0000-7000-8000-0000000000ad");
    static final UUID IMAGE = UUID.fromString("0199f5a0-0000-7000-8000-0000000000f1");
    static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

    final CatalogFakes.Products products = new CatalogFakes.Products();
    final CatalogFakes.Stocks stocks = new CatalogFakes.Stocks();
    final CatalogFakes.Categories categories = new CatalogFakes.Categories();
    final CatalogFakes.Sellers sellers = new CatalogFakes.Sellers();
    final CatalogFakes.RecordingOutbox outbox = new CatalogFakes.RecordingOutbox();
    final CatalogFakes.RecordingAuditLog auditLog = new CatalogFakes.RecordingAuditLog();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final ProductSnapshotFactory snapshots =
            new ProductSnapshotFactory(categories, sellers, stocks);

    final RegisterProductUseCase register =
            new RegisterProductUseCase(
                    products, stocks, categories, sellers, snapshots, outbox, clock);
    final PublishProductUseCase publish =
            new PublishProductUseCase(products, sellers, snapshots, outbox, clock);
    final StopProductUseCase stop = new StopProductUseCase(products, outbox, clock);
    final ResumeProductUseCase resume = new ResumeProductUseCase(products, sellers, outbox, clock);
    final BanProductUseCase ban = new BanProductUseCase(products, outbox, auditLog, clock);
    final SuspendSellerUseCase suspend = new SuspendSellerUseCase(sellers, products, outbox, clock);
    final ReinstateSellerUseCase reinstate =
            new ReinstateSellerUseCase(sellers, products, outbox, clock);

    @BeforeEach
    void givenSeller() {
        sellers.save(Seller.approve(SELLER, "길동상회", NOW));
    }

    @Test
    @DisplayName("판매자가 중지·재개하면 revision을 올린 ProductStopped·ProductResumed를 낸다")
    void stopAndResume() {
        UUID productId = onSale();

        stop.execute(new ChangeSaleStatusCommand(SELLER, productId));
        stop.execute(new ChangeSaleStatusCommand(SELLER, productId));
        resume.execute(new ChangeSaleStatusCommand(SELLER, productId));

        assertThat(outbox.types())
                .containsExactly(
                        "ProductRegistered",
                        "ProductPublished",
                        "ProductStopped",
                        "ProductResumed");
        ProductStopped stopped = (ProductStopped) outbox.events.get(2).payload();
        assertThat(stopped.revision()).isEqualTo(3);
        assertThat(stopped.reason()).isEqualTo("SELLER");
    }

    @Test
    @DisplayName("다른 판매자의 상품은 중지·재개할 수 없다(404)")
    void otherSellerCannotStop() {
        UUID productId = onSale();

        assertThatThrownBy(
                        () ->
                                stop.execute(
                                        new ChangeSaleStatusCommand(UUID.randomUUID(), productId)))
                .hasFieldOrPropertyWithValue("code", "CATALOG_PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("관리자 제재는 ProductBanned와 감사 로그를 남기고, 다시 해도 한 번이다")
    void bans() {
        UUID productId = onSale();

        ban.execute(new BanProductCommand(ADMIN, productId, "허위 광고"));
        ban.execute(new BanProductCommand(ADMIN, productId, "허위 광고"));

        assertThat(products.rows.get(productId).status()).isEqualTo(ProductStatus.BANNED);
        assertThat(outbox.types())
                .endsWith("ProductBanned")
                .filteredOn("ProductBanned"::equals)
                .hasSize(1);
        ProductBanned banned = (ProductBanned) outbox.events.getLast().payload();
        assertThat(banned.sellerId()).isEqualTo(SELLER);
        assertThat(banned.reason()).isEqualTo("허위 광고");
        assertThat(auditLog.entries)
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.actorId()).isEqualTo(ADMIN.toString());
                            assertThat(entry.action()).isEqualTo("PRODUCT_BANNED");
                            assertThat(entry.targetId()).isEqualTo(productId.toString());
                            assertThat(entry.detail()).isEqualTo("허위 광고");
                        });
    }

    @Test
    @DisplayName("판매자 정지는 판매 중인 상품만 멈추고, 해제는 정지로 멈춘 상품만 다시 연다")
    void suspendAndReinstate() {
        UUID selling = onSale();
        UUID stoppedBySeller = onSale();
        stop.execute(new ChangeSaleStatusCommand(SELLER, stoppedBySeller));
        UUID draft = register.execute(registerCommand());
        int before = outbox.events.size();

        suspend.execute(new SellerStatusCommand(SELLER));
        suspend.execute(new SellerStatusCommand(SELLER));

        assertThat(products.rows.get(selling).status()).isEqualTo(ProductStatus.STOPPED);
        assertThat(products.rows.get(draft).status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(outbox.events.subList(before, outbox.events.size()))
                .extracting(CatalogFakes.Appended::aggregateId)
                .containsExactly(selling.toString());
        assertThatThrownBy(() -> register.execute(registerCommand()))
                .hasFieldOrPropertyWithValue("code", "CATALOG_SELLER_SUSPENDED");
        assertThatThrownBy(
                        () -> resume.execute(new ChangeSaleStatusCommand(SELLER, stoppedBySeller)))
                .hasFieldOrPropertyWithValue("code", "CATALOG_SELLER_SUSPENDED");
        assertThatThrownBy(() -> publish.execute(new PublishProductCommand(SELLER, draft)))
                .hasFieldOrPropertyWithValue("code", "CATALOG_SELLER_SUSPENDED");

        reinstate.execute(new SellerStatusCommand(SELLER));

        assertThat(products.rows.get(selling).status()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(products.rows.get(stoppedBySeller).status()).isEqualTo(ProductStatus.STOPPED);
        assertThat(outbox.types()).last().isEqualTo("ProductResumed");
    }

    @Test
    @DisplayName("사본이 없는 판매자의 정지·해제 이벤트는 아무것도 하지 않는다")
    void unknownSeller() {
        suspend.execute(new SellerStatusCommand(UUID.randomUUID()));
        reinstate.execute(new SellerStatusCommand(UUID.randomUUID()));

        assertThat(outbox.events).isEmpty();
    }

    UUID onSale() {
        UUID productId = register.execute(registerCommand());
        publish.execute(new PublishProductCommand(SELLER, productId));
        return productId;
    }

    static RegisterProductCommand registerCommand() {
        return new RegisterProductCommand(
                SELLER,
                WOMEN,
                "반팔 티셔츠",
                "면 100%",
                List.of(IMAGE),
                List.of(),
                List.of(new RegisterProductCommand.Sku(List.of(), 19_900, 1)));
    }
}
