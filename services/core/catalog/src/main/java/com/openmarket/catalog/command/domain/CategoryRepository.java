package com.openmarket.catalog.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 카테고리 저장소(읽기 전용). 구현은 {@code infra}에 있다. */
public interface CategoryRepository {

    Optional<Category> findById(UUID id);

    boolean existsByParentId(UUID parentId);
}
