package com.openmarket.catalog.infra.persistence;

import com.openmarket.catalog.command.domain.Category;
import com.openmarket.catalog.command.domain.CategoryRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** {@link CategoryRepository}의 Spring Data JPA 구현. */
public interface JpaCategoryRepository extends Repository<Category, UUID>, CategoryRepository {}
