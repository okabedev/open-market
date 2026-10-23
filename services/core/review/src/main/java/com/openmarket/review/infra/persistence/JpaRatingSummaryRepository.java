package com.openmarket.review.infra.persistence;

import com.openmarket.review.command.domain.RatingSummary;
import com.openmarket.review.command.domain.RatingSummaryRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** {@link RatingSummaryRepository}의 Spring Data JPA 구현. */
public interface JpaRatingSummaryRepository
        extends Repository<RatingSummary, UUID>, RatingSummaryRepository {

    @Override
    default RatingSummary lockOrCreate(UUID productId, Instant now) {
        insertEmptyIfAbsent(productId, now);
        return findForUpdateById(productId)
                .orElseThrow(() -> new IllegalStateException("요약 행을 넣은 뒤에도 없다: " + productId));
    }

    /**
     * 빈 요약(리비전 0)을 넣는다. 같은 상품의 첫 리뷰가 동시에 오면 뒤 트랜잭션은 앞 트랜잭션이 끝날 때까지 기다렸다가 0행으로 끝난다(유니크 충돌 예외 없음).
     */
    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    "INSERT INTO rating_summary (product_id, rating_count, rating_sum, revision,"
                            + " updated_at) VALUES (:productId, 0, 0, 0, :now)"
                            + " ON CONFLICT (product_id) DO NOTHING",
            nativeQuery = true)
    int insertEmptyIfAbsent(@Param("productId") UUID productId, @Param("now") Instant now);

    /** {@code SELECT ... FOR UPDATE}. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RatingSummary s where s.productId = :productId")
    Optional<RatingSummary> findForUpdateById(@Param("productId") UUID productId);
}
