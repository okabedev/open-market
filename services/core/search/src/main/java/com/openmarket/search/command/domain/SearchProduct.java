package com.openmarket.search.command.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 검색 상품(docs/glossary.md "검색 상품"). catalog 상품 하나의 검색용 사본이다. 상품 이벤트는 상품 {@code revision}이, 품절 전환은
 * {@code availabilityRevision}이 저장된 것보다 클 때만 반영한다(docs/architecture/search.md "색인").
 *
 * <p>품절 전환이 상품 스냅샷보다 먼저 오면 상태와 내용 없이 판매 가능 여부만 가진다. 이때는 검색에 나오지 않는다.
 */
public final class SearchProduct {

    private final UUID productId;
    private long revision;
    private @Nullable SearchProductStatus status;
    private @Nullable Content content;
    private boolean inStock;
    private long availabilityRevision;
    private final @Nullable DocumentVersion version;

    private SearchProduct(
            UUID productId,
            long revision,
            @Nullable SearchProductStatus status,
            @Nullable Content content,
            boolean inStock,
            long availabilityRevision,
            @Nullable DocumentVersion version) {
        this.productId = productId;
        this.revision = revision;
        this.status = status;
        this.content = content;
        this.inStock = inStock;
        this.availabilityRevision = availabilityRevision;
        this.version = version;
    }

    /** 아직 받은 이벤트가 없는 상품. 저장소에 없을 때 만든다. */
    public static SearchProduct newProduct(UUID productId) {
        return new SearchProduct(productId, 0, null, null, false, 0, null);
    }

    /** 저장소에서 읽은 값으로 되살린다. */
    public static SearchProduct restore(
            UUID productId,
            long revision,
            @Nullable SearchProductStatus status,
            @Nullable Content content,
            boolean inStock,
            long availabilityRevision,
            @Nullable DocumentVersion version) {
        return new SearchProduct(
                productId, revision, status, content, inStock, availabilityRevision, version);
    }

    /**
     * 상품 스냅샷({@code ProductRegistered}, {@code ProductPublished}, {@code ProductChanged})을 반영한다. 상품
     * 부분과 판매 가능 여부는 각자의 리비전으로 따로 판단한다.
     *
     * @return 무엇이든 바꿨으면 true
     */
    public boolean applySnapshot(Snapshot snapshot) {
        boolean changed = false;
        if (snapshot.revision() > revision) {
            revision = snapshot.revision();
            status = snapshot.status();
            content = snapshot.content();
            changed = true;
        }
        return changeAvailability(snapshot.inStock(), snapshot.availabilityRevision()) || changed;
    }

    /**
     * 판매 상태 이벤트({@code ProductStopped}, {@code ProductResumed}, {@code ProductBanned})를 반영한다.
     *
     * @return 더 새로운 이벤트라 반영했으면 true
     */
    public boolean changeStatus(long revision, SearchProductStatus status) {
        if (revision <= this.revision) {
            return false;
        }
        this.revision = revision;
        this.status = status;
        return true;
    }

    /**
     * 품절 전환({@code StockAvailabilityChanged})을 반영한다.
     *
     * @return 더 새로운 판매 가능 리비전이라 반영했으면 true
     */
    public boolean changeAvailability(boolean inStock, long availabilityRevision) {
        if (availabilityRevision <= this.availabilityRevision) {
            return false;
        }
        this.inStock = inStock;
        this.availabilityRevision = availabilityRevision;
        return true;
    }

    /** 판매 중이고 내용을 아는 상품만 검색에 나온다(SRC-3). 품절이어도 나온다(뒤로 간다). */
    public boolean isSearchable() {
        return status == SearchProductStatus.ON_SALE && content != null;
    }

    public UUID productId() {
        return productId;
    }

    public long revision() {
        return revision;
    }

    public @Nullable SearchProductStatus status() {
        return status;
    }

    public @Nullable Content content() {
        return content;
    }

    public boolean inStock() {
        return inStock;
    }

    public long availabilityRevision() {
        return availabilityRevision;
    }

    /** 저장소에서 읽은 버전. 새로 만든 상품이면 null이다. */
    public @Nullable DocumentVersion version() {
        return version;
    }

    /**
     * 검색에 쓰는 상품 내용.
     *
     * @param categoryPath 대분류부터 상품 카테고리까지
     * @param thumbnailFileId 첫 이미지. 없으면 null
     * @param minPrice SKU 가격 중 가장 낮은 값(원)
     * @param publishedAt 판매를 시작한 시각. 시작한 적이 없으면 null
     */
    public record Content(
            UUID sellerId,
            String storeName,
            UUID categoryId,
            List<UUID> categoryPath,
            String name,
            String description,
            @Nullable UUID thumbnailFileId,
            long minPrice,
            @Nullable Instant publishedAt) {

        public Content {
            categoryPath = List.copyOf(categoryPath);
        }
    }

    /** 상품 스냅샷 중 검색에 쓰는 부분. */
    public record Snapshot(
            long revision,
            SearchProductStatus status,
            Content content,
            boolean inStock,
            long availabilityRevision) {}
}
