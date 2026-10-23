package com.openmarket.review.command.application;

import com.openmarket.review.command.domain.ReviewEligibility;
import com.openmarket.review.command.domain.ReviewEligibilityRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매확정한 주문 항목마다 리뷰 작성 자격을 만든다(docs/architecture/review.md "리뷰 작성 자격"). 이미 있는 항목은 넘긴다. 같은 이벤트는 리스너의
 * 처리 기록이 거르고, 다른 이벤트 ID로 다시 와도 주문 항목 ID(기본 키)로 한 번만 생긴다.
 */
@Service
public class CreateReviewEligibilitiesUseCase {

    private final ReviewEligibilityRepository eligibilities;
    private final Clock clock;

    public CreateReviewEligibilitiesUseCase(
            ReviewEligibilityRepository eligibilities, Clock clock) {
        this.eligibilities = eligibilities;
        this.clock = clock;
    }

    @Transactional
    public void execute(CreateReviewEligibilitiesCommand command) {
        Instant now = clock.instant();
        for (CreateReviewEligibilitiesCommand.Line line : command.lines()) {
            if (eligibilities.existsById(line.orderLineId())) {
                continue;
            }
            eligibilities.save(
                    ReviewEligibility.create(
                            new ReviewEligibility.Purchase(
                                    line.orderLineId(),
                                    command.orderId(),
                                    command.sellerOrderId(),
                                    line.productId(),
                                    line.productName(),
                                    line.optionValues(),
                                    command.sellerId(),
                                    command.buyerId(),
                                    command.confirmedAt()),
                            now));
        }
    }
}
