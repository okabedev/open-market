package com.openmarket.catalog.infra.persistence;

import com.openmarket.catalog.command.domain.StockAdjustment;
import com.openmarket.catalog.command.domain.StockAdjustmentRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** {@link StockAdjustmentRepository}의 Spring Data JPA 구현. */
public interface JpaStockAdjustmentRepository
        extends Repository<StockAdjustment, UUID>, StockAdjustmentRepository {}
