package com.openmarket.search.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.search.command.domain.ConcurrentSearchProductUpdateException;
import com.openmarket.search.command.domain.SearchProduct;
import com.openmarket.search.command.domain.SearchProductRepository;
import com.openmarket.search.command.domain.SearchProductStatus;
import com.openmarket.search.command.domain.SearchProductWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 상품 이벤트·품절 전환 이벤트를 검색 상품에 반영한다. */
class IndexingUseCasesTest {

    static final UUID PRODUCT = UUID.fromString("0199f5a0-0000-7000-8000-0000000000b1");

    final InMemoryProducts products = new InMemoryProducts();
    final SearchProductWriter writer = new SearchProductWriter(products);
    final IndexProductSnapshotUseCase indexSnapshot = new IndexProductSnapshotUseCase(writer);
    final ChangeProductStatusUseCase changeStatus = new ChangeProductStatusUseCase(writer);
    final ChangeStockAvailabilityUseCase changeAvailability =
            new ChangeStockAvailabilityUseCase(writer);

    @Test
    @DisplayName("판매 시작 스냅샷을 색인하고, 판매 중지·재개·제재로 상태를 바꾼다")
    void indexesAndChangesStatus() {
        indexSnapshot.execute(snapshot(2, "ON_SALE", true, 1));
        assertThat(products.rows.get(PRODUCT).isSearchable()).isTrue();
        assertThat(products.rows.get(PRODUCT).content())
                .extracting(SearchProduct.Content::thumbnailFileId)
                .isEqualTo(UUID.fromString("0199f5a0-0000-7000-8000-0000000000f1"));

        changeStatus.execute(
                new ChangeProductStatusCommand(
                        PRODUCT, 3, ChangeProductStatusCommand.Status.STOPPED));
        assertThat(products.rows.get(PRODUCT).isSearchable()).isFalse();

        changeStatus.execute(
                new ChangeProductStatusCommand(
                        PRODUCT, 4, ChangeProductStatusCommand.Status.ON_SALE));
        changeStatus.execute(
                new ChangeProductStatusCommand(
                        PRODUCT, 5, ChangeProductStatusCommand.Status.BANNED));
        assertThat(products.rows.get(PRODUCT).status()).isEqualTo(SearchProductStatus.BANNED);
    }

    @Test
    @DisplayName("품절 전환이 스냅샷보다 먼저 와도 판매 가능 여부를 잃지 않는다")
    void availabilityBeforeSnapshot() {
        changeAvailability.execute(new ChangeStockAvailabilityCommand(PRODUCT, false, 2));
        indexSnapshot.execute(snapshot(2, "ON_SALE", true, 1));

        SearchProduct product = products.rows.get(PRODUCT);
        assertThat(product.inStock()).isFalse();
        assertThat(product.isSearchable()).isTrue();
    }

    @Test
    @DisplayName("바뀌는 것이 없으면 쓰지 않는다")
    void skipsNoop() {
        indexSnapshot.execute(snapshot(2, "ON_SALE", true, 1));
        int writes = products.writes;

        indexSnapshot.execute(snapshot(2, "ON_SALE", true, 1));
        changeAvailability.execute(new ChangeStockAvailabilityCommand(PRODUCT, true, 1));

        assertThat(products.writes).isEqualTo(writes);
    }

    @Test
    @DisplayName("다른 리스너가 먼저 고쳐 충돌하면 다시 읽어 반영한다. 계속 충돌하면 예외로 재시도에 맡긴다")
    void retriesOnConflict() {
        indexSnapshot.execute(snapshot(2, "ON_SALE", true, 1));
        products.conflicts = 2;

        changeAvailability.execute(new ChangeStockAvailabilityCommand(PRODUCT, false, 2));
        assertThat(products.rows.get(PRODUCT).inStock()).isFalse();

        products.conflicts = 100;
        assertThatThrownBy(
                        () ->
                                changeAvailability.execute(
                                        new ChangeStockAvailabilityCommand(PRODUCT, true, 3)))
                .isInstanceOf(ConcurrentSearchProductUpdateException.class);
    }

    static IndexProductSnapshotCommand snapshot(
            long revision, String status, boolean inStock, long availabilityRevision) {
        return new IndexProductSnapshotCommand(
                PRODUCT,
                revision,
                status,
                UUID.fromString("0199f5a0-0000-7000-8000-0000000000aa"),
                "길동상회",
                UUID.fromString("0199f000-0000-7000-8000-000000000101"),
                List.of(
                        UUID.fromString("0199f000-0000-7000-8000-000000000100"),
                        UUID.fromString("0199f000-0000-7000-8000-000000000101")),
                "반팔 티셔츠",
                "면 100%",
                List.of(UUID.fromString("0199f5a0-0000-7000-8000-0000000000f1")),
                19_900,
                inStock,
                availabilityRevision,
                Instant.parse("2026-10-20T08:00:00Z"));
    }

    /** 저장할 때마다 버전을 올리는 가짜 저장소. {@code conflicts}만큼 저장을 충돌로 실패시킨다. */
    static final class InMemoryProducts implements SearchProductRepository {

        final Map<UUID, SearchProduct> rows = new HashMap<>();
        int conflicts;
        int writes;

        @Override
        public Optional<SearchProduct> findById(UUID productId) {
            return Optional.ofNullable(rows.get(productId)).map(InMemoryProducts::copy);
        }

        @Override
        public void save(SearchProduct product) {
            if (conflicts > 0) {
                conflicts--;
                throw new ConcurrentSearchProductUpdateException(product.productId());
            }
            writes++;
            rows.put(product.productId(), copy(product));
        }

        private static SearchProduct copy(SearchProduct p) {
            return SearchProduct.restore(
                    p.productId(),
                    p.revision(),
                    p.status(),
                    p.content(),
                    p.inStock(),
                    p.availabilityRevision(),
                    null);
        }
    }
}
