package com.openmarket.order.command.domain;

import com.openmarket.common.core.id.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 주문 항목(docs/glossary.md "주문 항목"). SKU 하나와 수량, 주문 당시 상품명·옵션·카테고리·가격 스냅샷이다. {@link SellerOrder}에
 * 딸린다.
 */
@Entity
@Table(name = "order_line")
public class OrderLine {

    @Id private UUID id;

    // SellerOrder.lines(mappedBy)의 주인 쪽이라 JPA가 seller_order_id를 쓰는 데 쓴다. 코드에서는 읽지 않는다.
    @SuppressWarnings("UnusedVariable")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_order_id", nullable = false)
    private SellerOrder sellerOrder;

    @Column(nullable = false)
    private int position;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "option_name_1")
    private @Nullable String optionName1;

    @Column(name = "option_value_1")
    private @Nullable String optionValue1;

    @Column(name = "option_name_2")
    private @Nullable String optionName2;

    @Column(name = "option_value_2")
    private @Nullable String optionValue2;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long amount;

    protected OrderLine() {}

    OrderLine(SellerOrder sellerOrder, int position, OrderItem item) {
        this.id = Ids.newId();
        this.sellerOrder = sellerOrder;
        this.position = position;
        this.skuId = item.skuId();
        this.productId = item.productId();
        this.productName = item.productName();
        this.categoryId = item.categoryId();
        this.optionName1 = at(item.optionNames(), 0);
        this.optionValue1 = at(item.optionValues(), 0);
        this.optionName2 = at(item.optionNames(), 1);
        this.optionValue2 = at(item.optionValues(), 1);
        this.unitPrice = item.unitPrice();
        this.quantity = item.quantity();
        this.amount = Math.multiplyExact(item.unitPrice(), item.quantity());
    }

    private static @Nullable String at(List<String> values, int index) {
        return index < values.size() ? values.get(index) : null;
    }

    public UUID id() {
        return id;
    }

    public UUID skuId() {
        return skuId;
    }

    public UUID productId() {
        return productId;
    }

    public UUID categoryId() {
        return categoryId;
    }

    public long unitPrice() {
        return unitPrice;
    }

    public String productName() {
        return productName;
    }

    public int quantity() {
        return quantity;
    }

    public long amount() {
        return amount;
    }

    public List<String> optionValues() {
        List<String> values = new ArrayList<>(2);
        if (optionValue1 != null) {
            values.add(optionValue1);
        }
        if (optionValue2 != null) {
            values.add(optionValue2);
        }
        return List.copyOf(values);
    }
}
