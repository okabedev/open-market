package com.openmarket.archbad.command.domain;

import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.core.types.dsl.EntityPathBase;

public class QSpringyOrder extends EntityPathBase<SpringyOrder> {
    public static final QSpringyOrder springyOrder = new QSpringyOrder("springyOrder");

    public QSpringyOrder(String variable) {
        super(SpringyOrder.class, PathMetadataFactory.forVariable(variable));
    }
}
