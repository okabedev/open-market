package com.openmarket.auth.infra.persistence;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** {@link AccountRepository}의 Spring Data JPA 구현. 메서드는 도메인 인터페이스에서 이름으로 만든다. */
public interface JpaAccountRepository extends Repository<Account, UUID>, AccountRepository {}
