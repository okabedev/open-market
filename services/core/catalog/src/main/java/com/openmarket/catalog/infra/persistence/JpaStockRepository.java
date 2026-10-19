package com.openmarket.catalog.infra.persistence;

import com.openmarket.catalog.command.domain.Stock;
import com.openmarket.catalog.command.domain.StockRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** {@link StockRepository}의 Spring Data JPA 구현. */
public interface JpaStockRepository extends Repository<Stock, UUID>, StockRepository {}
