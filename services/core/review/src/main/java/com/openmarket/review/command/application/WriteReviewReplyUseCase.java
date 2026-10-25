package com.openmarket.review.command.application;

import com.openmarket.review.command.domain.Review;
import com.openmarket.review.command.domain.ReviewNotFoundException;
import com.openmarket.review.command.domain.ReviewReply;
import com.openmarket.review.command.domain.ReviewReplyRepository;
import com.openmarket.review.command.domain.ReviewRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자가 리뷰에 답글을 달거나 고친다(REV-3, docs/architecture/review.md "답글"). 리뷰 행을 잠가 같은 리뷰의 첫 답글이 동시에 와도 하나만
 * 만든다. 이벤트는 내지 않는다(답글 알림 없음).
 */
@Service
public class WriteReviewReplyUseCase {

    private final ReviewRepository reviews;
    private final ReviewReplyRepository replies;
    private final Clock clock;

    public WriteReviewReplyUseCase(
            ReviewRepository reviews, ReviewReplyRepository replies, Clock clock) {
        this.reviews = reviews;
        this.replies = replies;
        this.clock = clock;
    }

    @Transactional
    public void execute(WriteReviewReplyCommand command) {
        Review review =
                reviews.findForUpdateById(command.reviewId())
                        .filter(r -> r.isSoldBy(command.sellerId()))
                        .orElseThrow(ReviewNotFoundException::new);
        Instant now = clock.instant();
        Optional<ReviewReply> existing = replies.findById(review.id());
        if (existing.isEmpty()) {
            replies.save(ReviewReply.write(review, command.sellerId(), command.content(), now));
        } else if (existing.get().edit(command.content(), now)) {
            replies.save(existing.get());
        }
    }
}
