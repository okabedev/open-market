package com.openmarket.archbad.command.domain;

import org.springframework.util.Assert;

/** 위반: 도메인이 Spring에 의존한다. */
public class SpringyOrder {
    public SpringyOrder(String id) {
        Assert.hasText(id, "id");
    }
}
