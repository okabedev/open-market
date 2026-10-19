package com.openmarket.catalog.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductTest {

    static final UUID SELLER = UUID.fromString("0199f5a0-0000-7000-8000-0000000000aa");
    static final UUID CATEGORY = UUID.fromString("0199f000-0000-7000-8000-000000000101");
    static final UUID IMAGE = UUID.fromString("0199f5a0-0000-7000-8000-0000000000f1");
    static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

    @Test
    @DisplayName("등록한 상품은 DRAFT이고, 옵션 조합마다 SKU를 갖고, 최저가를 안다")
    void registersDraft() {
        Product product =
                register(List.of("색상", "사이즈"), sku("블랙", "M", 19_900), sku("블랙", "L", 21_900));

        assertThat(product.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(product.optionNames()).containsExactly("색상", "사이즈");
        assertThat(product.skus())
                .extracting(Sku::optionValues)
                .containsExactly(List.of("블랙", "M"), List.of("블랙", "L"));
        assertThat(product.minPrice()).isEqualTo(19_900);
        assertThat(product.revision()).isZero();
        assertThat(product.publishedAt()).isNull();
    }

    @Test
    @DisplayName("옵션이 없으면 SKU 하나에 옵션 값이 없다")
    void registersWithoutOptions() {
        Product product = register(List.of(), sku(15_000));

        assertThat(product.skus())
                .singleElement()
                .extracting(Sku::optionValues)
                .isEqualTo(List.of());
    }

    @Test
    @DisplayName("이름·설명·이미지·옵션·SKU·가격 규칙을 어기면 CATALOG_INVALID_INPUT이다")
    void rejectsInvalidInput() {
        assertInvalid(
                () ->
                        Product.register(
                                UUID.randomUUID(),
                                SELLER,
                                CATEGORY,
                                details(" ", "설명", List.of()),
                                List.of(),
                                List.of(sku(1_000)),
                                NOW));
        assertInvalid(
                () ->
                        Product.register(
                                UUID.randomUUID(),
                                SELLER,
                                CATEGORY,
                                details("a".repeat(101), "설명", List.of()),
                                List.of(),
                                List.of(sku(1_000)),
                                NOW));
        assertInvalid(
                () ->
                        Product.register(
                                UUID.randomUUID(),
                                SELLER,
                                CATEGORY,
                                details("이름", "", List.of()),
                                List.of(),
                                List.of(sku(1_000)),
                                NOW));
        assertInvalid(
                () ->
                        Product.register(
                                UUID.randomUUID(),
                                SELLER,
                                CATEGORY,
                                details("이름", "설명", images(11)),
                                List.of(),
                                List.of(sku(1_000)),
                                NOW));
        assertInvalid(
                () ->
                        Product.register(
                                UUID.randomUUID(),
                                SELLER,
                                CATEGORY,
                                details("이름", "설명", List.of(IMAGE, IMAGE)),
                                List.of(),
                                List.of(sku(1_000)),
                                NOW));
        assertInvalid(() -> register(List.of("색상", "사이즈", "소재"), sku("a", "b", "c", 1_000)));
        assertInvalid(() -> register(List.of("색상", "색상"), sku("a", "b", 1_000)));
        assertInvalid(() -> register(List.of("색상"), sku("블랙", "M", 1_000)));
        assertInvalid(() -> register(List.of("색상"), sku("블랙", 1_000), sku("블랙", 2_000)));
        assertInvalid(() -> register(List.of()));
        assertInvalid(() -> register(List.of(), sku(1_000), sku(2_000)));
        assertInvalid(() -> register(List.of(), sku(90)));
        assertInvalid(() -> register(List.of(), sku(1_005)));
        assertInvalid(() -> register(List.of(), sku(100_000_010)));
        List<SkuInput> many = new ArrayList<>();
        IntStream.range(0, 101).forEach(i -> many.add(sku("v" + i, 1_000)));
        assertInvalid(() -> register(List.of("번호"), many.toArray(SkuInput[]::new)));
    }

    @Test
    @DisplayName("이미지가 있어야 판매를 시작할 수 있고, 시작하면 ON_SALE이 되고 시각을 남긴다")
    void publishes() {
        Product noImage = register(List.of(), sku(1_000));
        assertThatThrownBy(() -> noImage.publish(NOW))
                .hasFieldOrPropertyWithValue("code", "CATALOG_PRODUCT_NOT_PUBLISHABLE");

        Product product =
                Product.register(
                        UUID.randomUUID(),
                        SELLER,
                        CATEGORY,
                        details("이름", "설명", List.of(IMAGE)),
                        List.of(),
                        List.of(sku(1_000)),
                        NOW);
        assertThat(product.publish(NOW)).isTrue();
        assertThat(product.status()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(product.publishedAt()).isEqualTo(NOW);
        assertThat(product.publish(NOW.plusSeconds(60))).isFalse();
        assertThat(product.publishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("수정하면 내용과 기존 SKU 가격이 바뀌고, 옵션 값이 같은 새 줄은 새 SKU가 된다")
    void updatesContentAndSkus() {
        Product product = register(List.of("색상"), sku("블랙", 10_000));
        UUID black = product.skus().getFirst().id();

        List<Sku> added =
                product.update(
                        CATEGORY,
                        details("새 이름", "새 설명", List.of(IMAGE)),
                        List.of(
                                new SkuInput(black, List.of("블랙"), 12_000),
                                new SkuInput(null, List.of("화이트"), 13_000)),
                        NOW.plusSeconds(10));

        assertThat(product.name()).isEqualTo("새 이름");
        assertThat(product.imageFileIds()).containsExactly(IMAGE);
        assertThat(added).singleElement().extracting(Sku::optionValues).isEqualTo(List.of("화이트"));
        assertThat(product.skus()).extracting(Sku::price).containsExactly(12_000L, 13_000L);
        assertThat(product.updatedAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    @DisplayName("수정할 때 기존 SKU를 빼거나 옵션 값을 바꾸면 CATALOG_INVALID_INPUT이다")
    void rejectsSkuRemovalOrOptionChange() {
        Product product = register(List.of("색상"), sku("블랙", 10_000), sku("화이트", 10_000));
        UUID black = product.skus().get(0).id();
        UUID white = product.skus().get(1).id();

        assertInvalid(
                () ->
                        product.update(
                                CATEGORY,
                                details("이름", "설명", List.of()),
                                List.of(new SkuInput(black, List.of("블랙"), 10_000)),
                                NOW));
        assertInvalid(
                () ->
                        product.update(
                                CATEGORY,
                                details("이름", "설명", List.of()),
                                List.of(
                                        new SkuInput(black, List.of("레드"), 10_000),
                                        new SkuInput(white, List.of("화이트"), 10_000)),
                                NOW));
        assertInvalid(
                () ->
                        product.update(
                                CATEGORY,
                                details("이름", "설명", List.of()),
                                List.of(
                                        new SkuInput(black, List.of("블랙"), 10_000),
                                        new SkuInput(UUID.randomUUID(), List.of("레드"), 10_000),
                                        new SkuInput(white, List.of("화이트"), 10_000)),
                                NOW));
    }

    @Test
    @DisplayName("이벤트를 낼 때마다 revision이 1씩 오른다")
    void revisionIncreases() {
        Product product = register(List.of(), sku(1_000));

        assertThat(product.nextRevision()).isEqualTo(1);
        assertThat(product.nextRevision()).isEqualTo(2);
        assertThat(product.revision()).isEqualTo(2);
    }

    @Test
    @DisplayName("재고의 판매 가능 수량은 실재고에서 예약을 뺀 값이다")
    void stockAvailability() {
        Stock stock = Stock.create(UUID.randomUUID(), UUID.randomUUID(), 3, NOW);

        assertThat(stock.available()).isEqualTo(3);
        assertThat(stock.inStock()).isTrue();
        assertThat(Stock.create(UUID.randomUUID(), UUID.randomUUID(), 0, NOW).inStock()).isFalse();
        assertInvalid(() -> Stock.create(UUID.randomUUID(), UUID.randomUUID(), -1, NOW));
        assertInvalid(() -> Stock.create(UUID.randomUUID(), UUID.randomUUID(), 1_000_000, NOW));
    }

    static Product register(List<String> optionNames, SkuInput... skus) {
        return Product.register(
                UUID.randomUUID(),
                SELLER,
                CATEGORY,
                details("반팔 티셔츠", "면 100%", List.of()),
                optionNames,
                List.of(skus),
                NOW);
    }

    static Product.Details details(String name, String description, List<UUID> images) {
        return new Product.Details(name, description, images);
    }

    static SkuInput sku(long price) {
        return new SkuInput(null, List.of(), price);
    }

    static SkuInput sku(String value, long price) {
        return new SkuInput(null, List.of(value), price);
    }

    static SkuInput sku(String first, String second, long price) {
        return new SkuInput(null, List.of(first, second), price);
    }

    static SkuInput sku(String first, String second, String third, long price) {
        return new SkuInput(null, List.of(first, second, third), price);
    }

    static List<UUID> images(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }

    static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .hasFieldOrPropertyWithValue("code", "CATALOG_INVALID_INPUT");
    }
}
