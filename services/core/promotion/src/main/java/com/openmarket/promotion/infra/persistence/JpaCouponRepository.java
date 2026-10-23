package com.openmarket.promotion.infra.persistence;

import com.openmarket.promotion.command.domain.Coupon;
import com.openmarket.promotion.command.domain.CouponRepository;
import com.openmarket.promotion.command.domain.CouponStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** {@link CouponRepository}의 Spring Data JPA 구현. */
public interface JpaCouponRepository extends Repository<Coupon, UUID>, CouponRepository {

    /** {@code SELECT ... FOR UPDATE}. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Coupon> findForUpdateById(UUID id);

    /** 스칼라 조회라 영속성 컨텍스트가 아니라 행을 읽는다. */
    @Override
    @Query("select c.status from Coupon c where c.id = :id")
    Optional<CouponStatus> findStatusById(@Param("id") UUID id);

    @Override
    default boolean incrementIssuedCount(UUID id, Instant now) {
        return incrementIssuedCountRow(id, now) == 1;
    }

    /** 조건을 모두 만족할 때만 올린다. 행 잠금으로 동시 요청이 차례로 지나가고 CHECK 제약도 수량을 지킨다. */
    @Modifying
    @Query(
            value =
                    "UPDATE coupon SET issued_count = issued_count + 1 WHERE id = :id"
                            + " AND status = 'ISSUING' AND issue_start_at <= :now"
                            + " AND issue_end_at > :now AND issued_count < quantity",
            nativeQuery = true)
    int incrementIssuedCountRow(@Param("id") UUID id, @Param("now") Instant now);
}
