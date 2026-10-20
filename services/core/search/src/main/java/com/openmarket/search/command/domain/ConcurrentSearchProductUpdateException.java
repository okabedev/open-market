package com.openmarket.search.command.domain;

import java.util.UUID;

/** 읽은 뒤 다른 쓰기가 먼저 문서를 바꿨다. 다시 읽어 반영한다. */
public class ConcurrentSearchProductUpdateException extends RuntimeException {

    public ConcurrentSearchProductUpdateException(UUID productId) {
        super("검색 상품을 동시에 고쳤다: " + productId);
    }
}
