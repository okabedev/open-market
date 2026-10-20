package com.openmarket.search.command.application;

import com.openmarket.search.command.domain.SearchProduct;
import com.openmarket.search.command.domain.SearchProductStatus;
import com.openmarket.search.command.domain.SearchProductWriter;
import org.springframework.stereotype.Service;

/**
 * 상품 스냅샷({@code ProductRegistered}, {@code ProductPublished}, {@code ProductChanged})을 검색 상품에
 * 반영한다(SRC-1).
 */
@Service
public class IndexProductSnapshotUseCase {

    private final SearchProductWriter writer;

    public IndexProductSnapshotUseCase(SearchProductWriter writer) {
        this.writer = writer;
    }

    public void execute(IndexProductSnapshotCommand command) {
        SearchProduct.Snapshot snapshot =
                new SearchProduct.Snapshot(
                        command.revision(),
                        SearchProductStatus.valueOf(command.status()),
                        new SearchProduct.Content(
                                command.sellerId(),
                                command.storeName(),
                                command.categoryId(),
                                command.categoryPath(),
                                command.name(),
                                command.description(),
                                command.imageFileIds().isEmpty()
                                        ? null
                                        : command.imageFileIds().getFirst(),
                                command.minPrice(),
                                command.publishedAt()),
                        command.inStock(),
                        command.availabilityRevision());
        writer.update(command.productId(), product -> product.applySnapshot(snapshot));
    }
}
