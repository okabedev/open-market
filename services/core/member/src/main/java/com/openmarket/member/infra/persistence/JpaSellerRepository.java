package com.openmarket.member.infra.persistence;

import com.openmarket.member.command.domain.Seller;
import com.openmarket.member.command.domain.SellerRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

/** {@link SellerRepository}의 Spring Data JPA 구현. */
public interface JpaSellerRepository extends Repository<Seller, UUID>, SellerRepository {

    /** {@code SELECT ... FOR UPDATE}. 정지·해제가 겹쳐도 이벤트는 상태 변경마다 하나다. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Seller> findForUpdateById(UUID id);
}
