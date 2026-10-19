package com.openmarket.catalog.command.application;

import java.util.UUID;

/** 상품 판매를 시작한다. */
public record PublishProductCommand(UUID sellerId, UUID productId) {}
