package com.openmarket.payment.command.application;

import java.util.UUID;

/** 관리자가 토스가 거절한 보상 취소를 다시 시도한다(docs/architecture/payment.md "관리자 재시도"). */
public record RetryPaymentCancelCommand(UUID adminId, UUID paymentId) {}
