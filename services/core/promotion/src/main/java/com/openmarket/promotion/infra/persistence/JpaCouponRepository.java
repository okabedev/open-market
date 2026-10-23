package com.openmarket.promotion.infra.persistence;

import com.openmarket.promotion.command.domain.Coupon;
import com.openmarket.promotion.command.domain.CouponRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

/** {@link CouponRepository}의 Spring Data JPA 구현. */
public interface JpaCouponRepository extends Repository<Coupon, UUID>, CouponRepository {

    /** {@code SELECT ... FOR UPDATE}. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Coupon> findForUpdateById(UUID id);
}
