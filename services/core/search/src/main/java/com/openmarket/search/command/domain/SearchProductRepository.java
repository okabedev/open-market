package com.openmarket.search.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 검색 상품 저장소. 구현은 {@code infra}에 있다(Elasticsearch). */
public interface SearchProductRepository {

    /** 읽은 버전({@link SearchProduct#version()})을 함께 돌려준다. */
    Optional<SearchProduct> findById(UUID productId);

    /**
     * 읽은 버전이 그대로일 때만 쓴다. 버전이 없는(새) 문서는 아직 없을 때만 만든다.
     *
     * @throws ConcurrentSearchProductUpdateException 그 사이 다른 쓰기가 있었다
     */
    void save(SearchProduct product);
}
