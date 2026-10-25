package com.openmarket.review.command.application;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.review.command.domain.ModerationReason;
import com.openmarket.review.command.domain.RatingSummary;
import com.openmarket.review.command.domain.RatingSummaryChanged;
import com.openmarket.review.command.domain.RatingSummaryRepository;
import com.openmarket.review.command.domain.Review;
import com.openmarket.review.command.domain.ReviewNotFoundException;
import com.openmarket.review.command.domain.ReviewRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 리뷰를 숨기거나 다시 보인다(REV-3, ADM-2, docs/architecture/review.md "숨김"). 리뷰 행 → 별점 요약 행 순으로 잠그고, 상태가
 * 바뀌었을 때만 요약을 고쳐 {@code RatingSummaryChanged}를 outbox에 쓰고 감사 로그({@code REVIEW_HIDDEN}, {@code
 * REVIEW_SHOWN})를 남긴다. 같은 상태면 아무것도 하지 않는다. 없으면 404 {@code REVIEW_NOT_FOUND}, 사유가 틀리면 400 {@code
 * REVIEW_INVALID_INPUT}.
 */
@Service
public class ChangeReviewVisibilityUseCase {

    static final String AUDIT_TARGET = "REVIEW";

    private final ReviewRepository reviews;
    private final RatingSummaryRepository summaries;
    private final Outbox outbox;
    private final AuditLog auditLog;
    private final Clock clock;

    public ChangeReviewVisibilityUseCase(
            ReviewRepository reviews,
            RatingSummaryRepository summaries,
            Outbox outbox,
            AuditLog auditLog,
            Clock clock) {
        this.reviews = reviews;
        this.summaries = summaries;
        this.outbox = outbox;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public void execute(ChangeReviewVisibilityCommand command) {
        boolean visible = command.visible();
        ModerationReason checked = ModerationReason.of(command.reason());
        Review review =
                reviews.findForUpdateById(command.reviewId())
                        .orElseThrow(ReviewNotFoundException::new);
        Instant now = clock.instant();
        if (!(visible ? review.show(now) : review.hide(now))) {
            return;
        }
        reviews.save(review);

        RatingSummary summary = summaries.lockOrCreate(review.productId(), now);
        if (visible) {
            summary.add(review.rating(), now);
        } else {
            summary.remove(review.rating(), now);
        }
        summaries.save(summary);

        outbox.append(
                WriteReviewUseCase.RATING_SUMMARY_TOPIC,
                review.productId().toString(),
                RatingSummaryChanged.TYPE,
                RatingSummaryChanged.of(summary));
        auditLog.record(
                new AuditEntry(
                        command.adminId().toString(),
                        visible ? "REVIEW_SHOWN" : "REVIEW_HIDDEN",
                        AUDIT_TARGET,
                        review.id().toString(),
                        checked.value()));
    }
}
