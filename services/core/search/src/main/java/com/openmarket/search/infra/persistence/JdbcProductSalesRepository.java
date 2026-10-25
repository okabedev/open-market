package com.openmarket.search.infra.persistence;

import com.openmarket.search.command.domain.ProductSalesRepository;
import com.openmarket.search.command.domain.SearchProduct;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ProductSalesRepository}의 JDBC 구현. 더하기는 {@code INSERT ... ON CONFLICT DO UPDATE ...
 * RETURNING} 한 문장이라 행 잠금 아래에서 더하고, 같은 상품을 동시에 더하면 한쪽이 커밋까지 기다린다.
 */
@Repository
class JdbcProductSalesRepository implements ProductSalesRepository {

    private final JdbcClient jdbc;

    JdbcProductSalesRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markCounted(UUID sellerOrderId) {
        return jdbc.sql(
                                "INSERT INTO product_sales_order (seller_order_id, counted_at)"
                                        + " VALUES (?, now()) ON CONFLICT DO NOTHING")
                        .param(sellerOrderId)
                        .update()
                == 1;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public SearchProduct.Sales add(UUID productId, long quantity) {
        return jdbc.sql(
                        "INSERT INTO product_sales (product_id, sales_count, revision, updated_at)"
                                + " VALUES (?, ?, 1, now()) ON CONFLICT (product_id) DO UPDATE SET"
                                + " sales_count = product_sales.sales_count + excluded.sales_count,"
                                + " revision = product_sales.revision + 1, updated_at ="
                                + " excluded.updated_at RETURNING sales_count, revision")
                .param(productId)
                .param(quantity)
                .query((rs, row) -> new SearchProduct.Sales(rs.getLong(1), rs.getLong(2)))
                .single();
    }
}
