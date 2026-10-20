package com.openmarket.order.infra.persistence;

import com.openmarket.order.command.domain.Cart;
import com.openmarket.order.command.domain.CartRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** {@link CartRepository}의 Spring Data JPA 구현. */
public interface JpaCartRepository extends Repository<Cart, UUID>, CartRepository {

    // 테이블 이름만 쓴다. 스키마("order"는 예약어)는 연결의 search_path가 정한다(docs/architecture/data.md).
    @Override
    @Modifying
    @Query(
            value =
                    "INSERT INTO cart (member_id, updated_at) VALUES (:memberId, now())"
                            + " ON CONFLICT (member_id) DO NOTHING",
            nativeQuery = true)
    void createIfAbsent(@Param("memberId") UUID memberId);

    /** {@code SELECT ... FOR UPDATE}. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Cart> findForUpdateByMemberId(UUID memberId);
}
