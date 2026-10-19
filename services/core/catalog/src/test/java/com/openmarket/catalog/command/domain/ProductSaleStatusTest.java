package com.openmarket.catalog.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 판매 중지·재개, 관리자 제재, 판매자 정지 반영(CAT-2, CAT-5, SEL-3). */
class ProductSaleStatusTest {

    static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");
    static final UUID CATEGORY = UUID.fromString("0199f000-0000-7000-8000-000000000101");

    @Test
    @DisplayName("판매자가 중지하면 STOPPED가 되고, 판매자가 다시 열 수 있다. 같은 동작을 다시 하면 바뀌지 않는다")
    void sellerStopsAndResumes() {
        Product product = onSale();

        assertThat(product.stop(StopReason.SELLER, NOW)).isTrue();
        assertThat(product.status()).isEqualTo(ProductStatus.STOPPED);
        assertThat(product.stopReason()).isEqualTo(StopReason.SELLER);
        assertThat(product.stop(StopReason.SELLER, NOW)).isFalse();

        assertThat(product.resume(NOW)).isTrue();
        assertThat(product.status()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(product.stopReason()).isNull();
        assertThat(product.resume(NOW)).isFalse();
    }

    @Test
    @DisplayName("등록 중인 상품은 중지·재개할 수 없다")
    void draftCannotStopOrResume() {
        Product draft = draft();

        assertStatusError(() -> draft.stop(StopReason.SELLER, NOW), "CATALOG_INVALID_STATUS");
        assertStatusError(() -> draft.resume(NOW), "CATALOG_INVALID_STATUS");
    }

    @Test
    @DisplayName("판매자 정지로 멈춘 상품은 판매자가 다시 열 수 없고, 정지 해제 때만 다시 열린다")
    void suspensionStopsUntilReinstated() {
        Product product = onSale();

        assertThat(product.stop(StopReason.SELLER_SUSPENDED, NOW)).isTrue();
        assertStatusError(() -> product.resume(NOW), "CATALOG_SELLER_SUSPENDED");

        assertThat(product.resumeAfterReinstatement(NOW)).isTrue();
        assertThat(product.status()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("판매자가 직접 멈춘 상품은 정지가 와도 사유가 바뀌지 않고, 정지 해제에도 그대로 멈춰 있다")
    void sellerStopSurvivesSuspension() {
        Product product = onSale();
        product.stop(StopReason.SELLER, NOW);

        assertThat(product.stop(StopReason.SELLER_SUSPENDED, NOW)).isFalse();
        assertThat(product.stopReason()).isEqualTo(StopReason.SELLER);
        assertThat(product.resumeAfterReinstatement(NOW)).isFalse();
        assertThat(product.status()).isEqualTo(ProductStatus.STOPPED);
    }

    @Test
    @DisplayName("관리자 제재는 사유가 있어야 하고, 제재된 상품은 판매자가 재개·수정·판매 시작할 수 없다")
    void banBlocksSeller() {
        Product product = onSale();

        assertThatThrownBy(() -> product.ban(" ", NOW))
                .hasFieldOrPropertyWithValue("code", "CATALOG_INVALID_INPUT");
        assertThat(product.ban("허위 광고", NOW)).isTrue();
        assertThat(product.status()).isEqualTo(ProductStatus.BANNED);
        assertThat(product.banReason()).isEqualTo("허위 광고");
        assertThat(product.ban("다른 사유", NOW)).isFalse();
        assertThat(product.banReason()).isEqualTo("허위 광고");

        assertStatusError(() -> product.resume(NOW), "CATALOG_PRODUCT_BANNED");
        assertStatusError(() -> product.stop(StopReason.SELLER, NOW), "CATALOG_PRODUCT_BANNED");
        assertThat(product.stop(StopReason.SELLER_SUSPENDED, NOW)).isFalse();
        assertThat(product.resumeAfterReinstatement(NOW)).isFalse();
        assertStatusError(
                () ->
                        product.update(
                                CATEGORY,
                                new Product.Details("이름", "설명", List.of()),
                                List.of(
                                        new SkuInput(
                                                product.skus().getFirst().id(), List.of(), 1_000)),
                                NOW),
                "CATALOG_PRODUCT_BANNED");
    }

    @Test
    @DisplayName("등록 중인 상품도 제재할 수 있다")
    void banDraft() {
        Product draft = draft();

        assertThat(draft.ban("불법 상품", NOW)).isTrue();
        assertStatusError(() -> draft.publish(NOW), "CATALOG_PRODUCT_BANNED");
    }

    @Test
    @DisplayName("판매자 사본은 정지·해제가 바뀔 때만 true를 돌려준다")
    void sellerSuspension() {
        Seller seller = Seller.approve(UUID.randomUUID(), "길동상회", NOW);

        assertThat(seller.suspend(NOW)).isTrue();
        assertThat(seller.suspend(NOW)).isFalse();
        assertThat(seller.status()).isEqualTo(SellerStatus.SUSPENDED);
        assertThat(seller.reinstate(NOW)).isTrue();
        assertThat(seller.reinstate(NOW)).isFalse();
        assertThat(seller.status()).isEqualTo(SellerStatus.ACTIVE);
    }

    static Product draft() {
        return Product.register(
                UUID.randomUUID(),
                UUID.randomUUID(),
                CATEGORY,
                new Product.Details("반팔 티셔츠", "면 100%", List.of(UUID.randomUUID())),
                List.of(),
                List.of(new SkuInput(null, List.of(), 1_000)),
                NOW);
    }

    static Product onSale() {
        Product product = draft();
        product.publish(NOW);
        return product;
    }

    static void assertStatusError(Runnable action, String code) {
        assertThatThrownBy(action::run).hasFieldOrPropertyWithValue("code", code);
    }
}
