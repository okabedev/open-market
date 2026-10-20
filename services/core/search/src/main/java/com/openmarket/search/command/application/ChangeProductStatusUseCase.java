package com.openmarket.search.command.application;

import com.openmarket.search.command.domain.SearchProductStatus;
import com.openmarket.search.command.domain.SearchProductWriter;
import org.springframework.stereotype.Service;

/** 판매 중지·재개·제재를 검색 상품 상태에 반영한다. 판매 중이 아니면 검색에서 빠진다(SRC-3). */
@Service
public class ChangeProductStatusUseCase {

    private final SearchProductWriter writer;

    public ChangeProductStatusUseCase(SearchProductWriter writer) {
        this.writer = writer;
    }

    public void execute(ChangeProductStatusCommand command) {
        SearchProductStatus status = SearchProductStatus.valueOf(command.status().name());
        writer.update(
                command.productId(), product -> product.changeStatus(command.revision(), status));
    }
}
