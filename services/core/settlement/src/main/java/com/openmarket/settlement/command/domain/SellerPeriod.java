package com.openmarket.settlement.command.domain;

import java.time.Instant;
import java.util.UUID;

/** 확정할 판매자·정산 기간 하나. */
public record SellerPeriod(UUID sellerId, Instant periodStart, Instant periodEnd) {}
