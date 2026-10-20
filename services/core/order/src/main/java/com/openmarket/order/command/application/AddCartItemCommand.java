package com.openmarket.order.command.application;

import java.util.UUID;

public record AddCartItemCommand(UUID memberId, UUID skuId, int quantity) {}
