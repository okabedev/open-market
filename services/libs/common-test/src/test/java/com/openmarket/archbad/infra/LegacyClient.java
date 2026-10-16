package com.openmarket.archbad.infra;

import com.openmarket.archbad.ui.web.OrderController;

/** 위반: infra가 ui에 의존한다. */
public class LegacyClient {
    public void call() {
        new OrderController().create("legacy");
    }
}
