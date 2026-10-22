package com.openmarket.payment.command.application;

import java.util.UUID;

/** 관리자가 실패한 환불을 다시 시도한다(docs/architecture/payment.md "관리자 재시도"). */
public record RetryRefundCommand(UUID adminId, UUID refundId) {}
