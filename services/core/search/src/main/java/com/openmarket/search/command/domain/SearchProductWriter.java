package com.openmarket.search.command.domain;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * 검색 상품을 읽고, 바꾸고, 읽은 버전 그대로일 때만 쓴다. 두 토픽의 리스너가 같은 문서를 동시에 고치면 충돌한 쪽이 다시 읽어 반영한다. 계속 충돌하면 예외를 던져 리스너
 * 재시도에 맡긴다.
 */
public class SearchProductWriter {

    static final int MAX_ATTEMPTS = 5;

    private final SearchProductRepository products;

    public SearchProductWriter(SearchProductRepository products) {
        this.products = products;
    }

    /**
     * 상품 하나를 읽고 바꿔 쓴다. 저장소에 없으면 새 상품으로 시작한다.
     *
     * @param change 바꿨으면 true를 돌려준다. false면 쓰지 않는다
     * @return 썼으면 true
     */
    public boolean update(UUID productId, Predicate<SearchProduct> change) {
        for (int attempt = 1; ; attempt++) {
            SearchProduct product =
                    products.findById(productId)
                            .orElseGet(() -> SearchProduct.newProduct(productId));
            if (!change.test(product)) {
                return false;
            }
            try {
                products.save(product);
                return true;
            } catch (ConcurrentSearchProductUpdateException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }
}
