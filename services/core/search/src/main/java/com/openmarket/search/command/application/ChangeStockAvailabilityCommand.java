package com.openmarket.search.command.application;

import java.util.UUID;

/**
 * 품절 전환({@code StockAvailabilityChanged})을 반영한다.
 *
 * @param availabilityRevision 판매 가능 리비전
 */
public record ChangeStockAvailabilityCommand(
        UUID productId, boolean inStock, long availabilityRevision) {}
