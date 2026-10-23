package com.openmarket.review.command.application;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.review.command.domain.RatingSummary;
import com.openmarket.review.command.domain.RatingSummaryRepository;
import com.openmarket.review.command.domain.Review;
import com.openmarket.review.command.domain.ReviewEligibility;
import com.openmarket.review.command.domain.ReviewEligibilityRepository;
import com.openmarket.review.command.domain.ReviewRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** review 유스케이스 테스트용 가짜 구현. */
final class ReviewFakes {

    private ReviewFakes() {}

    static final class Eligibilities implements ReviewEligibilityRepository {
        final Map<UUID, ReviewEligibility> rows = new LinkedHashMap<>();

        @Override
        public ReviewEligibility save(ReviewEligibility eligibility) {
            rows.put(eligibility.orderLineId(), eligibility);
            return eligibility;
        }

        @Override
        public boolean existsById(UUID orderLineId) {
            return rows.containsKey(orderLineId);
        }

        @Override
        public Optional<ReviewEligibility> findForUpdateById(UUID orderLineId) {
            return Optional.ofNullable(rows.get(orderLineId));
        }
    }

    static final class Reviews implements ReviewRepository {
        final Map<UUID, Review> rows = new LinkedHashMap<>();

        @Override
        public Review save(Review review) {
            rows.put(review.id(), review);
            return review;
        }
    }

    static final class Summaries implements RatingSummaryRepository {
        final Map<UUID, RatingSummary> rows = new LinkedHashMap<>();

        @Override
        public RatingSummary lockOrCreate(UUID productId, Instant now) {
            return rows.computeIfAbsent(productId, id -> RatingSummary.empty(id, now));
        }

        @Override
        public RatingSummary save(RatingSummary summary) {
            rows.put(summary.productId(), summary);
            return summary;
        }
    }

    static final class RecordingOutbox implements Outbox {
        final List<Appended> events = new ArrayList<>();

        @Override
        public UUID append(
                String aggregateType,
                String aggregateId,
                String type,
                int schemaVersion,
                Object payload) {
            events.add(new Appended(aggregateType, aggregateId, type, payload));
            return UUID.randomUUID();
        }

        List<String> types() {
            return events.stream().map(Appended::type).toList();
        }

        record Appended(String aggregateType, String aggregateId, String type, Object payload) {}
    }
}
