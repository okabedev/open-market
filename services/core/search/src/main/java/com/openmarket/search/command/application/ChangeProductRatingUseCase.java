package com.openmarket.search.command.application;

import com.openmarket.search.command.domain.SearchProduct;
import com.openmarket.search.command.domain.SearchProductWriter;
import org.springframework.stereotype.Service;

/**
 * 별점 요약을 검색 상품에 반영한다(REV-2, docs/architecture/search.md "별점"). 리비전이 저장된 것보다 클 때만 반영하고, 아직 모르는 상품이면
 * 별점만 가진 문서를 만들어 둔다.
 */
@Service
public class ChangeProductRatingUseCase {

    private final SearchProductWriter writer;

    public ChangeProductRatingUseCase(SearchProductWriter writer) {
        this.writer = writer;
    }

    public void execute(ChangeProductRatingCommand command) {
        SearchProduct.Rating rating =
                new SearchProduct.Rating(
                        command.ratingAverage(), command.ratingCount(), command.revision());
        writer.update(command.productId(), product -> product.changeRating(rating));
    }
}
