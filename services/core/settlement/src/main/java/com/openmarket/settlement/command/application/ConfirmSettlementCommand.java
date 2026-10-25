package com.openmarket.settlement.command.application;

import java.time.Instant;
import java.util.UUID;

/** 판매자·정산 기간 하나의 정산을 확정한다. */
public record ConfirmSettlementCommand(UUID sellerId, Instant periodStart) {}
