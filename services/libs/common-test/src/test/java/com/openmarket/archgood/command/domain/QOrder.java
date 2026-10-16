package com.openmarket.archgood.command.domain;

import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.StringPath;

public class QOrder extends EntityPathBase<Order> {
    public static final QOrder order = new QOrder("order");
    public final StringPath id = createString("id");

    public QOrder(String variable) {
        super(Order.class, PathMetadataFactory.forVariable(variable));
    }
}
