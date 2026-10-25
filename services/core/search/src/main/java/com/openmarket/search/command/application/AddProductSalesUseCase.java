package com.openmarket.search.command.application;

import com.openmarket.search.command.domain.ProductSalesRepository;
import com.openmarket.search.command.domain.SearchProduct;
import com.openmarket.search.command.domain.SearchProductWriter;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매확정 수량을 상품 판매량에 더한다(docs/architecture/search.md "판매량"). 리스너의 멱등 처리 트랜잭션 안에서 하위 주문을 기록하고({@code
 * product_sales_order}), 상품마다 {@code product_sales} 행을 잠가 더한 뒤 커밋 전에 더한 결과와 리비전을 문서에 쓴다. 문서 쓰기가
 * 실패하면 트랜잭션이 되돌아가 다시 처리한다. 여러 상품은 상품 ID 순으로 잠근다(교착 방지).
 */
@Service
public class AddProductSalesUseCase {

    private final ProductSalesRepository sales;
    private final SearchProductWriter writer;

    public AddProductSalesUseCase(ProductSalesRepository sales, SearchProductWriter writer) {
        this.sales = sales;
        this.writer = writer;
    }

    @Transactional
    public void execute(AddProductSalesCommand command) {
        if (!sales.markCounted(command.sellerOrderId())) {
            return;
        }
        Map<UUID, Long> quantities = new TreeMap<>();
        command.lines()
                .forEach(
                        line ->
                                quantities.merge(
                                        line.productId(), (long) line.quantity(), Long::sum));
        quantities.forEach(
                (productId, quantity) -> {
                    SearchProduct.Sales added = sales.add(productId, quantity);
                    writer.update(productId, product -> product.changeSales(added));
                });
    }
}
