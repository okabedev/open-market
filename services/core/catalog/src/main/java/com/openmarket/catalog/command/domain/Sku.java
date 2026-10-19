package com.openmarket.catalog.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 옵션 조합(docs/glossary.md "옵션 조합"). {@link Product}에 딸린다. 옵션 값은 만든 뒤 바꾸지 않는다. */
@Entity
@Table(name = "sku")
public class Sku {

    @Id private UUID id;

    // Product.skus(mappedBy)의 주인 쪽이라 JPA가 product_id를 쓰는 데 쓴다. 코드에서는 읽지 않는다.
    @SuppressWarnings("UnusedVariable")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int position;

    @Column(name = "option_value_1")
    private @Nullable String optionValue1;

    @Column(name = "option_value_2")
    private @Nullable String optionValue2;

    @Column(nullable = false)
    private long price;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Sku() {}

    Sku(
            UUID id,
            Product product,
            int position,
            List<String> optionValues,
            long price,
            Instant now) {
        this.id = id;
        this.product = product;
        this.position = position;
        this.optionValue1 = optionValues.isEmpty() ? null : optionValues.get(0);
        this.optionValue2 = optionValues.size() < 2 ? null : optionValues.get(1);
        this.price = price;
        this.createdAt = now;
    }

    void changePrice(long price) {
        this.price = price;
    }

    public UUID id() {
        return id;
    }

    /** 상품의 옵션 이름 순서대로. 옵션이 없으면 비어 있다. */
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

    public long price() {
        return price;
    }
}
