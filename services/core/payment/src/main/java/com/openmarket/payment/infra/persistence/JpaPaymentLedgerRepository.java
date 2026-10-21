package com.openmarket.payment.infra.persistence;

import com.openmarket.payment.command.domain.PaymentLedgerEntry;
import com.openmarket.payment.command.domain.PaymentLedgerRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** {@link PaymentLedgerRepository}의 Spring Data JPA 구현. 추가만 한다. */
public interface JpaPaymentLedgerRepository
        extends Repository<PaymentLedgerEntry, UUID>, PaymentLedgerRepository {}
