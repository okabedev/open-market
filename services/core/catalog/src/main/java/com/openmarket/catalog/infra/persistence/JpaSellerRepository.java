package com.openmarket.catalog.infra.persistence;

import com.openmarket.catalog.command.domain.Seller;
import com.openmarket.catalog.command.domain.SellerRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** {@link SellerRepository}의 Spring Data JPA 구현. */
public interface JpaSellerRepository extends Repository<Seller, UUID>, SellerRepository {}
