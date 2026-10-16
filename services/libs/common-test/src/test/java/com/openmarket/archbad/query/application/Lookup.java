package com.openmarket.archbad.query.application;

import com.openmarket.archbad.command.domain.QSpringyOrder;

/** 위반: 이름이 QueryService로 끝나지 않고, DAO가 아닌데 Q-타입을 쓴다. */
public class Lookup {
    public String path() {
        return QSpringyOrder.springyOrder.toString();
    }
}
