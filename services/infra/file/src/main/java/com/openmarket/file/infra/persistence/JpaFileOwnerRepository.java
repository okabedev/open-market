package com.openmarket.file.infra.persistence;

import com.openmarket.file.command.domain.FileOwner;
import com.openmarket.file.command.domain.FileOwnerRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** {@link FileOwnerRepository}의 Spring Data JPA 구현. */
public interface JpaFileOwnerRepository extends Repository<FileOwner, UUID>, FileOwnerRepository {}
