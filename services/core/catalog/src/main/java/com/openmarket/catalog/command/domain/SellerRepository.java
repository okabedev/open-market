package com.openmarket.catalog.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 판매자 사본 저장소. 구현은 {@code infra}에 있다. */
public interface SellerRepository {

    Seller save(Seller seller);

    Optional<Seller> findById(UUID id);
}
