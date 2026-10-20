package com.openmarket.search.command.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchProductTest {

    static final UUID PRODUCT = UUID.fromString("0199f5a0-0000-7000-8000-0000000000b1");
    static final Instant PUBLISHED = Instant.parse("2026-10-20T08:00:00Z");

    @Test
    @DisplayName("스냅샷은 저장된 것보다 큰 revision만 반영하고, 판매 중이면 검색에 나온다")
    void appliesNewerSnapshotOnly() {
        SearchProduct product = SearchProduct.newProduct(PRODUCT);

        assertThat(product.applySnapshot(snapshot(2, SearchProductStatus.ON_SALE, "새 이름", true, 1)))
                .isTrue();
        assertThat(product.applySnapshot(snapshot(1, SearchProductStatus.DRAFT, "옛 이름", true, 1)))
                .isFalse();
        assertThat(product.applySnapshot(snapshot(2, SearchProductStatus.DRAFT, "같은 판", true, 1)))
                .isFalse();

        assertThat(product.revision()).isEqualTo(2);
        assertThat(product.content()).extracting(SearchProduct.Content::name).isEqualTo("새 이름");
        assertThat(product.isSearchable()).isTrue();
    }

    @Test
    @DisplayName("판매 상태 이벤트는 더 큰 revision일 때만 상태를 바꾸고, 판매 중이 아니면 검색에서 빠진다")
    void changesStatus() {
        SearchProduct product = SearchProduct.newProduct(PRODUCT);
        product.applySnapshot(snapshot(2, SearchProductStatus.ON_SALE, "티셔츠", true, 1));

        assertThat(product.changeStatus(3, SearchProductStatus.STOPPED)).isTrue();
        assertThat(product.isSearchable()).isFalse();
        assertThat(product.changeStatus(3, SearchProductStatus.ON_SALE)).isFalse();
        assertThat(product.changeStatus(4, SearchProductStatus.ON_SALE)).isTrue();
        assertThat(product.isSearchable()).isTrue();
    }

    @Test
    @DisplayName("판매 가능 여부는 판매 가능 리비전이 클 때만 바뀐다. 스냅샷과 품절 전환 이벤트 중 큰 쪽이 남는다")
    void availabilityFollowsItsOwnRevision() {
        SearchProduct product = SearchProduct.newProduct(PRODUCT);

        assertThat(product.changeAvailability(false, 3)).isTrue();
        assertThat(product.isSearchable()).isFalse();

        product.applySnapshot(snapshot(2, SearchProductStatus.ON_SALE, "티셔츠", true, 2));
        assertThat(product.inStock()).isFalse();
        assertThat(product.availabilityRevision()).isEqualTo(3);
        assertThat(product.isSearchable()).isTrue();

        assertThat(product.changeAvailability(true, 3)).isFalse();
        assertThat(product.changeAvailability(true, 4)).isTrue();
        assertThat(product.inStock()).isTrue();
    }

    @Test
    @DisplayName("오래된 스냅샷이라도 판매 가능 리비전이 더 크면 판매 가능 여부만 반영한다")
    void staleSnapshotStillCarriesNewerAvailability() {
        SearchProduct product = SearchProduct.newProduct(PRODUCT);
        product.applySnapshot(snapshot(5, SearchProductStatus.ON_SALE, "새 이름", true, 1));

        assertThat(product.applySnapshot(snapshot(4, SearchProductStatus.DRAFT, "옛 이름", false, 2)))
                .isTrue();

        assertThat(product.revision()).isEqualTo(5);
        assertThat(product.status()).isEqualTo(SearchProductStatus.ON_SALE);
        assertThat(product.inStock()).isFalse();
    }

    static SearchProduct.Snapshot snapshot(
            long revision,
            SearchProductStatus status,
            String name,
            boolean inStock,
            long availabilityRevision) {
        return new SearchProduct.Snapshot(
                revision,
                status,
                new SearchProduct.Content(
                        UUID.fromString("0199f5a0-0000-7000-8000-0000000000aa"),
                        "길동상회",
                        UUID.fromString("0199f000-0000-7000-8000-000000000101"),
                        List.of(
                                UUID.fromString("0199f000-0000-7000-8000-000000000100"),
                                UUID.fromString("0199f000-0000-7000-8000-000000000101")),
                        name,
                        "면 100%",
                        null,
                        19_900,
                        PUBLISHED),
                inStock,
                availabilityRevision);
    }
}
