package com.openmarket.catalog.infra.persistence;

import com.openmarket.catalog.command.domain.Product;
import com.openmarket.catalog.command.domain.ProductRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

/** {@link ProductRepository}의 Spring Data JPA 구현. */
public interface JpaProductRepository extends Repository<Product, UUID>, ProductRepository {

    /** {@code SELECT ... FOR UPDATE}. 같은 상품의 수정이 겹쳐도 revision이 이벤트 순서와 같다. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findForUpdateById(UUID id);
}
