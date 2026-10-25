package com.openmarket.settlement.infra.persistence;

import com.openmarket.settlement.command.domain.SettlementItem;
import com.openmarket.settlement.command.domain.SettlementItemRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** {@link SettlementItemRepository}의 Spring Data JPA 구현. */
public interface JpaSettlementItemRepository
        extends Repository<SettlementItem, UUID>, SettlementItemRepository {}
