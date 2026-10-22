package com.openmarket.fulfillment.command.application;

import java.util.UUID;

/** 클레임의 환불이 끝났다({@code RefundCompleted}). */
public record CompleteClaimCommand(UUID claimId) {}
