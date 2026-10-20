package com.openmarket.order.command.application;

import java.util.UUID;

public record ChangeCartItemQuantityCommand(UUID memberId, UUID skuId, int quantity) {}
