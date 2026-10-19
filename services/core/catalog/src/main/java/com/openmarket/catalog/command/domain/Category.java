package com.openmarket.catalog.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 카테고리(docs/glossary.md "카테고리"). 2단계 트리이고 Flyway 시드로만 만든다. 상품은 소분류(자식이 없는 카테고리)에만 붙는다. */
@Entity
@Table(name = "category")
public class Category {

    @Id private UUID id;

    @Column(name = "parent_id")
    private @Nullable UUID parentId;

    @Column(nullable = false)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected Category() {}

    public Category(UUID id, @Nullable UUID parentId, String name, int displayOrder) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.displayOrder = displayOrder;
    }

    public UUID id() {
        return id;
    }

    public @Nullable UUID parentId() {
        return parentId;
    }

    public String name() {
        return name;
    }
}
