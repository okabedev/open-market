package com.openmarket.archbad.command.application;

import com.openmarket.archbad.command.domain.SpringyOrder;

/** 위반: Command가 도메인 타입을 노출한다. */
public record CreateOrderCommand(SpringyOrder order) {}
