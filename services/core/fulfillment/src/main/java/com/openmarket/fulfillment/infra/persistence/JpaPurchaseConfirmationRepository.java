package com.openmarket.fulfillment.infra.persistence;

import com.openmarket.fulfillment.command.domain.PurchaseConfirmation;
import com.openmarket.fulfillment.command.domain.PurchaseConfirmationRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** {@link PurchaseConfirmationRepository}의 Spring Data JPA 구현. */
public interface JpaPurchaseConfirmationRepository
        extends Repository<PurchaseConfirmation, UUID>, PurchaseConfirmationRepository {}
