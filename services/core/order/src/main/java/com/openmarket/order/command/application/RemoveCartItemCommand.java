package com.openmarket.order.command.application;

import java.util.UUID;

public record RemoveCartItemCommand(UUID memberId, UUID skuId) {}
