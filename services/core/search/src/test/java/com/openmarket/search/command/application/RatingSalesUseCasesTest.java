package com.openmarket.search.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.search.command.domain.ProductSalesRepository;
import com.openmarket.search.command.domain.SearchProduct;
import com.openmarket.search.command.domain.SearchProductWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 별점 요약과 판매량을 검색 상품에 반영한다(docs/architecture/search.md "별점", "판매량"). */
class RatingSalesUseCasesTest {

    static final UUID PRODUCT = UUID.fromString("0199f5a0-0000-7000-8000-0000000000b1");
    static final UUID OTHER = UUID.fromString("0199f5a0-0000-7000-8000-0000000000b2");

    final IndexingUseCasesTest.InMemoryProducts products =
            new IndexingUseCasesTest.InMemoryProducts();
    final SearchProductWriter writer = new SearchProductWriter(products);
    final InMemorySales sales = new InMemorySales();
    final ChangeProductRatingUseCase changeRating = new ChangeProductRatingUseCase(writer);
    final AddProductSalesUseCase addSales = new AddProductSalesUseCase(sales, writer);

    @Test
    @DisplayName("별점 요약은 리비전이 저장된 것보다 클 때만 반영한다. 아직 모르는 상품이면 별점만 가진 문서를 만든다")
    void changesRating() {
        changeRating.execute(new ChangeProductRatingCommand(PRODUCT, 2, new BigDecimal("4.5"), 2));
        changeRating.execute(new ChangeProductRatingCommand(PRODUCT, 1, new BigDecimal("5.0"), 1));

        SearchProduct product = products.rows.get(PRODUCT);
        assertThat(product.rating())
                .isEqualTo(new SearchProduct.Rating(new BigDecimal("4.5"), 2, 2));
        assertThat(product.isSearchable()).isFalse();

        changeRating.execute(new ChangeProductRatingCommand(PRODUCT, 0, null, 3));
        assertThat(products.rows.get(PRODUCT).rating())
                .isEqualTo(new SearchProduct.Rating(null, 0, 3));
    }

    @Test
    @DisplayName("구매확정의 상품별 수량을 더하고 더한 결과와 리비전을 문서에 쓴다. 같은 상품의 항목은 합쳐 한 번 더하고, 같은 하위 주문은 한 번만 센다")
    void addsSales() {
        addSales.execute(
                new AddProductSalesCommand(
                        UUID.randomUUID(),
                        List.of(
                                new AddProductSalesCommand.Line(OTHER, 1),
                                new AddProductSalesCommand.Line(PRODUCT, 2),
                                new AddProductSalesCommand.Line(PRODUCT, 3))));
        UUID sellerOrderId = UUID.randomUUID();
        AddProductSalesCommand again =
                new AddProductSalesCommand(
                        sellerOrderId, List.of(new AddProductSalesCommand.Line(PRODUCT, 1)));
        addSales.execute(again);
        // 같은 하위 주문이 다른 이벤트 ID로 다시 와도 한 번만 더한다
        addSales.execute(again);

        assertThat(products.rows.get(PRODUCT).sales()).isEqualTo(new SearchProduct.Sales(6, 2));
        assertThat(products.rows.get(OTHER).sales()).isEqualTo(new SearchProduct.Sales(1, 1));
        // 여러 상품을 잠글 때는 상품 ID 순이다(교착 방지)
        assertThat(sales.calls).containsExactly(PRODUCT, OTHER, PRODUCT);
    }

    @Test
    @DisplayName("판매량 문서 쓰기는 리비전이 더 클 때만 반영한다(늦게 끝난 쓰기가 앞의 값을 덮지 않는다)")
    void keepsNewerSales() {
        SearchProduct product = SearchProduct.newProduct(PRODUCT);

        assertThat(product.changeSales(new SearchProduct.Sales(5, 2))).isTrue();
        assertThat(product.changeSales(new SearchProduct.Sales(3, 1))).isFalse();
        assertThat(product.sales()).isEqualTo(new SearchProduct.Sales(5, 2));
        assertThat(product.rating()).isEqualTo(SearchProduct.Rating.NONE);
    }

    /** Postgres {@code product_sales}처럼 더하고 리비전을 올린다. */
    static final class InMemorySales implements ProductSalesRepository {
        final Map<UUID, SearchProduct.Sales> rows = new HashMap<>();
        final List<UUID> calls = new ArrayList<>();
        final java.util.Set<UUID> counted = new java.util.HashSet<>();

        @Override
        public boolean markCounted(UUID sellerOrderId) {
            return counted.add(sellerOrderId);
        }

        @Override
        public SearchProduct.Sales add(UUID productId, long quantity) {
            calls.add(productId);
            SearchProduct.Sales before = rows.getOrDefault(productId, SearchProduct.Sales.NONE);
            SearchProduct.Sales after =
                    new SearchProduct.Sales(before.count() + quantity, before.revision() + 1);
            rows.put(productId, after);
            return after;
        }
    }
}
