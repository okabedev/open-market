package com.openmarket.auth.infra.persistence;

import com.openmarket.auth.command.domain.EmailVerification;
import com.openmarket.auth.command.domain.EmailVerificationRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** {@link EmailVerificationRepository}의 Spring Data JPA 구현. */
public interface JpaEmailVerificationRepository
        extends Repository<EmailVerification, UUID>, EmailVerificationRepository {}
