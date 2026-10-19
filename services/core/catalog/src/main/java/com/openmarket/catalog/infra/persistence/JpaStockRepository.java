package com.openmarket.catalog.infra.persistence;

import com.openmarket.catalog.command.domain.Stock;
import com.openmarket.catalog.command.domain.StockRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

/** {@link StockRepository}의 Spring Data JPA 구현. */
public interface JpaStockRepository extends Repository<Stock, UUID>, StockRepository {

    /** {@code SELECT ... FOR UPDATE}. 같은 SKU의 조정이 겹쳐도 덮어쓰지 않는다. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Stock> findForUpdateBySkuId(UUID skuId);
}
