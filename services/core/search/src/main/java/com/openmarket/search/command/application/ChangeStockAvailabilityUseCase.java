package com.openmarket.search.command.application;

import com.openmarket.search.command.domain.SearchProductWriter;
import org.springframework.stereotype.Service;

/** 품절 전환을 검색 상품에 반영한다. 품절 상품은 검색 결과 뒤로 간다(SRC-3). 아직 모르는 상품이면 판매 가능 여부만 저장해 둔다. */
@Service
public class ChangeStockAvailabilityUseCase {

    private final SearchProductWriter writer;

    public ChangeStockAvailabilityUseCase(SearchProductWriter writer) {
        this.writer = writer;
    }

    public void execute(ChangeStockAvailabilityCommand command) {
        writer.update(
                command.productId(),
                product ->
                        product.changeAvailability(
                                command.inStock(), command.availabilityRevision()));
    }
}
