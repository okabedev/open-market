package com.openmarket.promotion.infra.persistence;

import com.openmarket.promotion.command.domain.IssuedCoupon;
import com.openmarket.promotion.command.domain.IssuedCouponRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** {@link IssuedCouponRepository}의 Spring Data JPA 구현. */
public interface JpaIssuedCouponRepository
        extends Repository<IssuedCoupon, UUID>, IssuedCouponRepository {

    /** {@code SELECT ... FOR UPDATE}, ID 순서. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IssuedCoupon i where i.id in :ids order by i.id")
    List<IssuedCoupon> findForUpdateByIdInOrderById(@Param("ids") Collection<UUID> ids);

    /** {@code SELECT ... FOR UPDATE}, ID 순서. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IssuedCoupon i where i.orderId = :orderId order by i.id")
    List<IssuedCoupon> findForUpdateByOrderIdOrderById(@Param("orderId") UUID orderId);

    @Override
    default boolean insertIfAbsent(IssuedCoupon issued) {
        return insertRow(
                        issued.id(),
                        issued.couponId(),
                        issued.memberId(),
                        issued.status().name(),
                        issued.issuedAt(),
                        issued.expiresAt(),
                        issued.updatedAt())
                == 1;
    }

    /** 유니크 {@code (coupon_id, member_id)}가 1인 1매의 마지막 방어선이다. 겹치면 예외 없이 0행이다. */
    @Modifying
    @Query(
            value =
                    "INSERT INTO issued_coupon (id, coupon_id, member_id, status, issued_at,"
                            + " expires_at, updated_at, version)"
                            + " VALUES (:id, :couponId, :memberId, :status, :issuedAt, :expiresAt,"
                            + " :updatedAt, 0) ON CONFLICT (coupon_id, member_id) DO NOTHING",
            nativeQuery = true)
    int insertRow(
            @Param("id") UUID id,
            @Param("couponId") UUID couponId,
            @Param("memberId") UUID memberId,
            @Param("status") String status,
            @Param("issuedAt") Instant issuedAt,
            @Param("expiresAt") Instant expiresAt,
            @Param("updatedAt") Instant updatedAt);

    @Override
    @Query(
            "select i.id from IssuedCoupon i where i.couponId = :couponId and i.memberId ="
                    + " :memberId")
    Optional<UUID> findIdByCouponIdAndMemberId(
            @Param("couponId") UUID couponId, @Param("memberId") UUID memberId);

    @Override
    @Query("select i.memberId from IssuedCoupon i where i.couponId = :couponId")
    List<UUID> findMemberIdsByCouponId(@Param("couponId") UUID couponId);
}
