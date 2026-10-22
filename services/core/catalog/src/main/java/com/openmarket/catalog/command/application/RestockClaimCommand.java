package com.openmarket.catalog.command.application;

import java.util.List;
import java.util.UUID;

/** 끝난 클레임의 재입고 항목({@code ClaimCompleted}). 재입고하지 않으면 빈 목록이다. */
public record RestockClaimCommand(UUID claimId, List<Line> lines) {

    public record Line(UUID skuId, int quantity) {}
}
