package com.openmarket.search.infra.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.OpType;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.openmarket.search.command.domain.ConcurrentSearchProductUpdateException;
import com.openmarket.search.command.domain.DocumentVersion;
import com.openmarket.search.command.domain.SearchProduct;
import com.openmarket.search.command.domain.SearchProductRepository;
import com.openmarket.search.command.domain.SearchProductStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * {@link SearchProductRepository}의 Elasticsearch 구현. 읽은 {@code _seq_no}·{@code _primary_term}이 그대로일
 * 때만 쓰고(낙관적 동시성), 새 문서는 없을 때만 만든다. 충돌(409)은 {@link ConcurrentSearchProductUpdateException}으로 바꾼다.
 */
@Repository
class ElasticsearchSearchProductRepository implements SearchProductRepository {

    private static final int CONFLICT = 409;

    private final ElasticsearchClient client;

    ElasticsearchSearchProductRepository(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public Optional<SearchProduct> findById(UUID productId) {
        GetResponse<SearchProductDocument> response;
        try {
            response =
                    client.get(
                            g -> g.index(ProductIndex.ALIAS).id(productId.toString()),
                            SearchProductDocument.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SearchProductDocument source = response.source();
        if (!response.found() || source == null) {
            return Optional.empty();
        }
        Long seqNo = response.seqNo();
        Long primaryTerm = response.primaryTerm();
        DocumentVersion version =
                seqNo == null || primaryTerm == null
                        ? null
                        : new DocumentVersion(seqNo, primaryTerm);
        return Optional.of(toDomain(source, version));
    }

    @Override
    public void save(SearchProduct product) {
        SearchProductDocument document = toDocument(product);
        @Nullable DocumentVersion version = product.version();
        try {
            client.index(
                    i -> {
                        i.index(ProductIndex.ALIAS)
                                .id(product.productId().toString())
                                .document(document);
                        if (version == null) {
                            i.opType(OpType.Create);
                        } else {
                            i.ifSeqNo(version.sequence()).ifPrimaryTerm(version.term());
                        }
                        return i;
                    });
        } catch (ElasticsearchException e) {
            if (e.status() == CONFLICT) {
                throw new ConcurrentSearchProductUpdateException(product.productId());
            }
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SearchProduct toDomain(
            SearchProductDocument source, @Nullable DocumentVersion version) {
        SearchProduct.Content content = null;
        if (source.name() != null
                && source.sellerId() != null
                && source.storeName() != null
                && source.categoryId() != null
                && source.description() != null
                && source.minPrice() != null) {
            List<String> path = source.categoryPath() == null ? List.of() : source.categoryPath();
            content =
                    new SearchProduct.Content(
                            UUID.fromString(source.sellerId()),
                            source.storeName(),
                            UUID.fromString(source.categoryId()),
                            path.stream().map(UUID::fromString).toList(),
                            source.name(),
                            source.description(),
                            source.thumbnailFileId() == null
                                    ? null
                                    : UUID.fromString(source.thumbnailFileId()),
                            source.minPrice(),
                            source.publishedAt());
        }
        return SearchProduct.restore(
                UUID.fromString(source.productId()),
                source.revision(),
                source.status() == null ? null : SearchProductStatus.valueOf(source.status()),
                content,
                source.inStock(),
                source.availabilityRevision(),
                version);
    }

    private static SearchProductDocument toDocument(SearchProduct product) {
        SearchProduct.Content content = product.content();
        SearchProductStatus status = product.status();
        return new SearchProductDocument(
                product.productId().toString(),
                product.revision(),
                status == null ? null : status.name(),
                content == null ? null : content.sellerId().toString(),
                content == null ? null : content.storeName(),
                content == null ? null : content.categoryId().toString(),
                content == null
                        ? null
                        : content.categoryPath().stream().map(UUID::toString).toList(),
                content == null ? null : content.name(),
                content == null ? null : content.description(),
                content == null || content.thumbnailFileId() == null
                        ? null
                        : content.thumbnailFileId().toString(),
                content == null ? null : content.minPrice(),
                content == null ? null : content.publishedAt(),
                product.inStock(),
                product.availabilityRevision());
    }
}
