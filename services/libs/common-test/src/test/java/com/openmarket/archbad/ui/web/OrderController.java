package com.openmarket.archbad.ui.web;

import com.openmarket.archbad.command.domain.SpringyOrder;

/** 위반: 진입점이 도메인을 직접 쓴다. */
public class OrderController {
    public SpringyOrder create(String id) {
        return new SpringyOrder(id);
    }
}
